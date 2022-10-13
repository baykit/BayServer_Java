<?xml version="1.0" encoding="Shift_JIS"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output indent="yes" encoding="ISO-8859-1" method="html"/>
<xsl:template match="/">
 <HTML>
  <HEAD>
   <TITLE>Bay-Menu</TITLE>
  </HEAD>
  <BODY BGCOLOR="c6c6ff">
    <TABLE BORDER="0" WIDTH="100%" CELLSPACING="0">
     <TR>
       <TD WIDTH="20%" VALIGN="top" NOWRAP="true">
           <xsl:apply-templates/>
       </TD>
     </TR>
    </TABLE>
  </BODY>
 </HTML>
</xsl:template>

<xsl:template match="group">
      <P><STRONG><xsl:value-of select="@title"/></STRONG></P>
        <UL>
          <xsl:for-each select="item">
             <LI>
               <A HREF="{@href}" TARGET="content">
                  <xsl:value-of select="@title"/>
               </A>
             </LI>
          </xsl:for-each>
        </UL>
</xsl:template>

</xsl:stylesheet>
