<html>
<body>
 <% 
    Cookie[] cookies = request.getCookies(); 
    int count = 1;
    for(int i = 0; i < cookies.length; i++) {
       if(cookies[i].getName().equals("count")) 
          count = Integer.parseInt(cookies[i].getValue()) + 1;
    }
  %>
 <h1>Cookie value = <%= count %></h1>
 <%
    Cookie cookie = new Cookie("count", Integer.toString(count));
    cookie.setMaxAge(10000); 
    response.addCookie(cookie);
  %> 
</body>
</html>
 