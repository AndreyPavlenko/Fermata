FROM mobiledevops/android-sdk-image:latest

COPY entrypoint.sh /etc/entrypoint.sh

RUN echo '[ -z "$FERMATA_PROJ_DIR" ] && . /etc/entrypoint.sh' >> /etc/bash.bashrc

ENTRYPOINT ["/etc/entrypoint.sh"]
CMD /bin/bash
