FROM postgres:16-alpine

#init script
COPY init-database.sh /docker-entrypoint-initdb.d/
RUN chmod +x /docker-entrypoint-initdb.d/init-database.sh