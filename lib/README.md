# lib/

Place the two jars the build needs in this folder. They are not committed to the repository, so you
download them once (the quick start in the main [README](../README.md#quick-start-for-beginners) shows
where this fits in).

1. **MySQL Connector/J.** Download the *Platform Independent* ZIP from
   https://dev.mysql.com/downloads/connector/j/, extract it and copy
   `mysql-connector-j-<version>.jar` here.
2. **Servlet API.** Copy `servlet-api.jar` from Tomcat's `lib/` folder here. It is used only to
   compile; Tomcat supplies it at run time and it is not packaged into the war.
3. Copy the MySQL jar into Tomcat's `lib/` folder too (Tomcat needs the driver when the app runs). If
   Tomcat is already running, restart it once.

Nothing else belongs in this folder.
