FROM alpine:3.13.1
LABEL Arpeet Gupta

# Setting up work directory
RUN apk add --no-cache bash && \
    apk add --update --no-cache ca-certificates git

# Copying Artifact to Image
ADD ./product /go/bin/product

ENTRYPOINT ["/go/bin/product"]

CMD ["--port", "8080"]
# Add `CMD` and Expose port for REST Endpoint 
EXPOSE 8080