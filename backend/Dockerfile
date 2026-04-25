FROM eclipse-temurin:21-jre-jammy

ENV TZ=UTC

RUN apt-get update && apt-get install -y \
    ffmpeg \
    python3 \
    python3-pip \
    curl \
    && python3 -m pip install --upgrade pip \
    && pip3 install --no-cache-dir -U yt-dlp \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY build/install/creator-content-hub/ /app/

# segurança
# criar usuário e diretórios com permissão correta
RUN useradd -m appuser \
    && mkdir -p /app /data \
    && chown -R appuser:appuser /app /data

USER appuser

EXPOSE 8080

CMD ["./bin/creator-content-hub"]