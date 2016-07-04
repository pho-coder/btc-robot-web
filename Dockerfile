FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/rocks.pho.btc-robot-web.jar /rocks.pho.btc-robot-web/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/rocks.pho.btc-robot-web/app.jar"]
