# CLI do Strix (pentest com agentes de IA) sem instalar nada no Mac. O Strix sobe o próprio sandbox
# (ghcr.io/usestrix/strix-sandbox) pelo socket do Docker; este container só roda o CLI.
FROM python:3.12-slim

RUN pip install --no-cache-dir strix-agent==1.7.0

ENTRYPOINT ["strix"]
