@rem user.timezone is used in logging

java                                                ^
        -Duser.timezone=America/Sao_Paulo           ^
        -Dlog4j.configurationFile=config/log4j2.xml ^
        -jar target/narina-rgc-1.0.0.jar
