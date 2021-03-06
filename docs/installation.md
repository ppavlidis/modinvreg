# Installation

This section describes the essential steps to deploy an RDP registry.

## Requirements

- Java 8+
- MySQL 5.5+ or equivalent
- SMTP mail server

## Obtain a distribution of RDP

Download the [latest jar distribution](https://github.com/PavlidisLab/rgr/releases/latest) from GitHub.

```bash
wget -O rdp.jar https://github.com/PavlidisLab/rgr/releases/download/v1.3.2/rdp-1.3.2.jar
```

The jar contains the core application, including an embedded webserver (Tomcat 8.5.x),
a task scheduler, an in-memory cache, and much more!

## Setup the MySQL database

Create the database and an associated user that the application will use to
store and retrieve data.

```sql
create database <database name> character set utf8mb4 collate utf8mb4_unicode_ci;
create user  '<database username>'@'%' identified by '<database password>';
grant all on rdp.* to '<database username>'@'%';
```

## Setup application.properties

In the working directory of the Web application, create an `application.properties`
file that contains at least the following entries:

```ini
spring.profiles.active=prod

spring.datasource.url=jdbc:mysql://<database host>:3306/<database name>
spring.datasource.username=<database username>
spring.datasource.password=<database password>

spring.mail.host=<mail host>
spring.mail.port=587
spring.mail.username=<mail username>
spring.mail.password=<mail password>
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

This file contains the database and SMTP credentials and various runtime
configurations. Make sure it's only readable by the user that will run the
instance.

Documentation for options with their default values are available in [application.properties](https://github.com/PavlidisLab/rgr/blob/development/src/main/resources/application.properties).

That should be enough to get the Web service started. Now you can launch it by
issuing the following command:

```bash
java -jar rdp.jar
```

If your email server is not properly configured, you will see an error from the
Spring Actuator health check. You can detect further issues by looking at the
`/admin/health` endpoint with administrative privilege.

## Create an administrative account

Use the registration page to create a user account and promote it to administrator privileges. Consider this to be a
sanity check for your database and email server setup.

Once created, obtain its user identifier:

```sql
select user_id from user where email = '<your user email>';
```

Then add the `ROLE_ADMIN` (`role_id` is `1`) role to that user:

```sql
insert into user_role (user_id, role_id) values ('<user_id>', 1);
```

Next, we will see how the Web application can be customized further.
