<html>
 <body>
  <jsp:useBean id="b" scope="page" class="demo.MyBean" />
  <h1>
   <jsp:getProperty name="b" property="hoge"/>
  </h1>
 </body>
</html>