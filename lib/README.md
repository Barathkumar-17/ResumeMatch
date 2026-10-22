# lib/

Place the **MySQL Connector/J** driver jar in this folder (it is not committed).

1. Download the *Platform Independent* ZIP from https://dev.mysql.com/downloads/connector/j/
2. Extract it and copy `mysql-connector-j-<version>.jar` here.
3. Copy the same jar into Tomcat's `lib/` folder and restart Tomcat.

The servlet API is supplied by Tomcat; nothing else belongs in this folder.
