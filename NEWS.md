# BayServer for Java

# 3.0.0

- [Core] Performes a significant overall refactoring.
- [Core] Enables I/O multiplexing using an event-driven API.
- [Core] Introduces a multiplexer type to allow flexible configuration of the I/O multiplexing method.
- [Core] Adopts the CIDR format for specifying source IP control.
- [CGI] Introduce the maxProcesses parameter to allow control over the number of processes to be started.

# 2.3.3

- [Core] Fixes the issue encountered when aborting GrandAgent.

# 2.3.2

- [Core] Fixes the issue encountered when aborting GrandAgent.

# 2.3.1

- [Core] Addresses potential issues arising from I/O errors.

# 2.3.0

- [CGI] Supports "timeout" parameter. (The timed-out CGI processes are killed)
- [Core] Improves the memusage output
- [Core] Fixes some bugs

# 2.2.1

- Fixes some bugs

# 2.2.0

- Supports downloads from the Maven Central Repository

# 2.1.0

- Refactors classes around GrandAgent and GrandAgentMonitor
- Fixes some bugs

# 2.0.2

- Translates some messages to Japanese
- Fixes HTTP/2 bugs
- Fixes Problem of POST request which causes 404
- Fixes problem on handling wp-cron.php of WordPress
- Fixes problem on handling admin-ajax.php of WordPress
- Fixes potential bugs


# 2.0.1

- Modifies bayserver.plan to avoid resolving host name


# 2.0.0

- First version
