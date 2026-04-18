package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.protocol.CommandUnPacker;
import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Dispatches incoming H2 frames to command handlers and enforces the
 * frame-level rules of RFC 7540 that are independent of any particular
 * frame body: header-block continuity (§6.2, §6.10), PUSH_PROMISE from
 * client (§8.2), SETTINGS ACK payload size (§6.5), stream-id presence
 * requirements (§6.x), silent discard of unknown frame types (§4.1),
 * and the per-stream state machine (§5.1).
 */
public class H2CommandUnPacker extends CommandUnPacker<H2Packet> {

    /**
     * Subset of RFC 7540 §5.1 states tracked by the server. {@code idle} is
     * represented by absence from {@link #streamStates}; the reserved states
     * are not needed because we never send or receive PUSH_PROMISE.
     */
    enum StreamState {
        OPEN,
        HALF_CLOSED_REMOTE,
        CLOSED,
    }

    H2CommandHandler cmdHandler;

    // RFC 7540 § 6.2 and § 6.10: a HEADERS/PUSH_PROMISE frame without
    // END_HEADERS starts a header block that must be terminated by a
    // CONTINUATION with END_HEADERS on the same stream. No other frame
    // may appear in between.
    boolean inHeaderBlock;
    int headerBlockStreamId;
    // END_STREAM bit from the HEADERS frame that started the current block;
    // it only takes effect once END_HEADERS (possibly on a later CONTINUATION)
    // finishes the block.
    boolean pendingEndStream;

    // Per-stream state. Idle streams are simply absent from this map.
    final Map<Integer, StreamState> streamStates = new HashMap<>();
    // Highest client-initiated (odd) stream id we have ever observed; lets us
    // distinguish "idle" from "closed" when a stream is absent from the map.
    int highestSeenStreamId;

    public H2CommandUnPacker(H2CommandHandler cmdHandler) {
        this.cmdHandler = cmdHandler;
        reset();
    }

    @Override
    public void reset() {
        inHeaderBlock = false;
        headerBlockStreamId = 0;
        pendingEndStream = false;
        streamStates.clear();
        highestSeenStreamId = 0;
    }

    @Override
    public NextSocketAction packetReceived(H2Packet pkt) throws IOException {

        if(BayLog.isDebugMode())
            BayLog.debug("h2: read packet typ=" + pkt.type() + " strmid=" + pkt.streamId + " len=" + pkt.dataLen() + " flgs=" + pkt.flags);

        int type = pkt.type();

        // RFC 7540 § 4.1: unknown frame types MUST be ignored and discarded.
        // Unknown types may appear inside a header block, in which case it is
        // still a PROTOCOL_ERROR because CONTINUATION must be the next frame.
        if (!isKnownType(type)) {
            if (inHeaderBlock)
                throw new ProtocolException("Unknown frame type " + type + " during header block");
            return NextSocketAction.Continue;
        }

        validateFrame(pkt);
        validateStreamState(pkt);

        H2Command cmd;
        switch (type) {
            case H2Type.Preface:
                cmd = new CmdPreface(pkt.streamId, pkt.flags);
                break;

            case H2Type.Headers:
                cmd = new CmdHeaders(pkt.streamId, pkt.flags);
                break;

            case H2Type.Priority:
                cmd = new CmdPriority(pkt.streamId, pkt.flags);
                break;

            case H2Type.Settings:
                cmd = new CmdSettings(pkt.streamId, pkt.flags);
                break;

            case H2Type.WindowUpdate:
                cmd = new CmdWindowUpdate(pkt.streamId, pkt.flags);
                break;

            case H2Type.Data:
                cmd = new CmdData(pkt.streamId, pkt.flags);
                break;

            case H2Type.Goaway:
                cmd = new CmdGoAway(pkt.streamId, pkt.flags);
                break;

            case H2Type.Ping:
                cmd = new CmdPing(pkt.streamId, pkt.flags);
                break;

            case H2Type.RstStream:
                cmd = new CmdRstStream(pkt.streamId);
                break;

            case H2Type.Continuation:
                cmd = new CmdContinuation(pkt.streamId);
                break;

            default:
                // Unreachable: isKnownType() above guards this.
                throw new IllegalStateException("Received packet: " + pkt);
        }

        updateHeaderBlockState(pkt);
        updateStreamState(pkt);

        cmd.unpack(pkt);
        return cmd.handle(cmdHandler);
    }

    private static boolean isKnownType(int type) {
        switch (type) {
            case H2Type.Preface:
            case H2Type.Data:
            case H2Type.Headers:
            case H2Type.Priority:
            case H2Type.RstStream:
            case H2Type.Settings:
            case H2Type.PushPromise:
            case H2Type.Ping:
            case H2Type.Goaway:
            case H2Type.WindowUpdate:
            case H2Type.Continuation:
                return true;
            default:
                return false;
        }
    }

    private void validateFrame(H2Packet pkt) throws ProtocolException {
        int type = pkt.type();
        int streamId = pkt.streamId;
        H2Flags flags = pkt.flags;

        // RFC 7540 § 6.10: while a header block is being received, the next
        // frame MUST be a CONTINUATION on the same stream.
        if (inHeaderBlock) {
            if (type != H2Type.Continuation)
                throw new ProtocolException(
                        "Expected CONTINUATION while in header block: got type=" + type);
            if (streamId != headerBlockStreamId)
                throw new ProtocolException(
                        "CONTINUATION on wrong stream: expected=" + headerBlockStreamId
                                + " got=" + streamId);
        } else if (type == H2Type.Continuation) {
            // § 6.10: CONTINUATION outside a header block is PROTOCOL_ERROR.
            throw new ProtocolException("Unexpected CONTINUATION frame outside header block");
        }

        // RFC 7540 § 6.x: per-frame stream-id rules.
        switch (type) {
            case H2Type.Data:
            case H2Type.Headers:
            case H2Type.Priority:
            case H2Type.RstStream:
            case H2Type.PushPromise:
            case H2Type.Continuation:
                if (streamId == 0)
                    throw new ProtocolException(
                            "Frame type " + type + " requires non-zero stream id");
                break;
            case H2Type.Settings:
            case H2Type.Ping:
            case H2Type.Goaway:
                if (streamId != 0)
                    throw new ProtocolException(
                            "Frame type " + type + " requires stream id 0, got " + streamId);
                break;
            // WindowUpdate: can be on stream 0 (connection) or a specific stream.
            default:
                break;
        }

        // RFC 7540 § 8.2: a server MUST NOT receive a PUSH_PROMISE frame
        // (clients are forbidden from sending it).
        if (type == H2Type.PushPromise)
            throw new ProtocolException("Server must not receive PUSH_PROMISE");

        // RFC 7540 § 6.5: SETTINGS with ACK must have an empty payload.
        if (type == H2Type.Settings && flags.ack() && pkt.dataLen() > 0)
            throw new ProtocolException("SETTINGS ACK must have no payload");
    }

    /**
     * RFC 7540 § 5.1: verify that the incoming frame is allowed in the current
     * stream state. Connection-level frames (stream id 0) are unaffected.
     * CONTINUATION is intentionally skipped here because its legality is
     * governed by the header-block rules checked in {@link #validateFrame}.
     */
    private void validateStreamState(H2Packet pkt) throws ProtocolException {
        int type = pkt.type();
        int streamId = pkt.streamId;
        if (streamId == 0 || type == H2Type.Continuation)
            return;

        StreamState state = streamStates.get(streamId);

        if (state == null) {
            // "idle" — no state recorded yet for this stream id. If the id is
            // below the high-water mark then this stream was implicitly closed
            // by § 5.1.1 (any HEADERS on a stream id N implicitly closes all
            // lower-numbered streams).
            boolean implicitlyClosed = streamId <= highestSeenStreamId;
            // RFC 7540 § 5.1.1: streams initiated by the client must have odd
            // stream ids, and ids must strictly increase.
            if (type == H2Type.Headers) {
                if ((streamId & 1) == 0)
                    throw new ProtocolException(
                            "Client HEADERS with even stream id " + streamId);
                if (implicitlyClosed)
                    throw new ProtocolException(
                            "Stream id " + streamId + " is not greater than the previous "
                                    + highestSeenStreamId);
                // RFC 7540 § 5.1.2: reject a new stream that would exceed the
                // advertised MAX_CONCURRENT_STREAMS (REFUSED_STREAM).
                int max = BayServer.harbor.maxToursPerShip();
                if (countActiveStreams() >= max)
                    throw new H2ProtocolException(H2ErrorCode.REFUSED_STREAM,
                            "Concurrent stream limit (" + max + ") exceeded");
            }
            switch (type) {
                case H2Type.Headers:
                case H2Type.Priority:
                    // HEADERS opens a new stream; PRIORITY is allowed on any
                    // state including idle.
                    return;
                case H2Type.RstStream:
                case H2Type.Data:
                case H2Type.WindowUpdate:
                    if (implicitlyClosed)
                        throw new ProtocolException(
                                "Frame type " + type + " on closed stream " + streamId);
                    throw new ProtocolException(
                            "Frame type " + type + " on idle stream " + streamId);
                default:
                    return;
            }
        }

        switch (state) {
            case OPEN:
                // Any allowed frame may arrive while the stream is open.
                // A second HEADERS on an open stream is a trailer section,
                // which § 8.1 requires to terminate the stream (END_STREAM).
                if (type == H2Type.Headers && !pkt.flags.endStream())
                    throw new ProtocolException(
                            "Trailer HEADERS on stream " + streamId + " missing END_STREAM");
                return;

            case HALF_CLOSED_REMOTE:
                // Client has already sent END_STREAM. DATA/HEADERS from the
                // client is a stream error STREAM_CLOSED (§ 5.1).
                if (type == H2Type.Data || type == H2Type.Headers)
                    throw new ProtocolException(
                            "Frame type " + type + " on half-closed (remote) stream " + streamId);
                return;

            case CLOSED:
                // A closed stream rejects everything except PRIORITY and
                // WINDOW_UPDATE (the latter may still be in-flight).
                if (type == H2Type.Priority || type == H2Type.WindowUpdate)
                    return;
                throw new ProtocolException(
                        "Frame type " + type + " on closed stream " + streamId);
        }
    }

    private int countActiveStreams() {
        int n = 0;
        for (StreamState s : streamStates.values()) {
            if (s != StreamState.CLOSED)
                n++;
        }
        return n;
    }

    private void updateHeaderBlockState(H2Packet pkt) {
        int type = pkt.type();
        // Only HEADERS / PUSH_PROMISE / CONTINUATION affect header-block state,
        // and those frames always have flags populated by the packet unpacker.
        if (type == H2Type.Headers || type == H2Type.PushPromise) {
            inHeaderBlock = !pkt.flags.endHeaders();
            headerBlockStreamId = pkt.streamId;
            pendingEndStream = pkt.flags.endStream();
        } else if (type == H2Type.Continuation) {
            if (pkt.flags.endHeaders())
                inHeaderBlock = false;
        }
    }

    /**
     * Transition the stream state machine based on the incoming frame.
     * Called after {@link #validateStreamState} has accepted the frame.
     */
    private void updateStreamState(H2Packet pkt) {
        int type = pkt.type();
        int streamId = pkt.streamId;
        if (streamId == 0)
            return;

        StreamState state = streamStates.get(streamId);

        switch (type) {
            case H2Type.Headers: {
                if (state == null) {
                    // Opening a new stream implicitly closes any lower-id
                    // streams that never saw an explicit close (§ 5.1.1).
                    if (streamId > highestSeenStreamId)
                        highestSeenStreamId = streamId;
                    // If END_STREAM and END_HEADERS are both set we jump
                    // straight to half-closed (remote); otherwise the
                    // transition waits until the header block completes.
                    state = StreamState.OPEN;
                    streamStates.put(streamId, state);
                    if (pkt.flags.endHeaders() && pkt.flags.endStream())
                        streamStates.put(streamId, StreamState.HALF_CLOSED_REMOTE);
                } else if (state == StreamState.OPEN) {
                    // Trailer section: RFC 7540 § 8.1 requires END_STREAM.
                    if (pkt.flags.endHeaders() && pkt.flags.endStream())
                        streamStates.put(streamId, StreamState.HALF_CLOSED_REMOTE);
                }
                break;
            }

            case H2Type.Continuation: {
                // The HEADERS' END_STREAM takes effect once the header block
                // closes via END_HEADERS on this CONTINUATION.
                if (pkt.flags.endHeaders() && pendingEndStream && state == StreamState.OPEN) {
                    streamStates.put(streamId, StreamState.HALF_CLOSED_REMOTE);
                }
                if (pkt.flags.endHeaders())
                    pendingEndStream = false;
                break;
            }

            case H2Type.Data: {
                if (pkt.flags.endStream() && state == StreamState.OPEN)
                    streamStates.put(streamId, StreamState.HALF_CLOSED_REMOTE);
                break;
            }

            case H2Type.RstStream: {
                // Either peer can reset; we treat the stream as closed.
                streamStates.put(streamId, StreamState.CLOSED);
                if (streamId > highestSeenStreamId)
                    highestSeenStreamId = streamId;
                break;
            }

            default:
                break;
        }
    }
}
