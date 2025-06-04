# Use Microsoft's Python base image with stable Ubuntu
FROM mcr.microsoft.com/playwright/python:v1.52.0-noble
# FROM  hub.byted.org/lab/playwright-python:v1.50.0-noble

COPY . /app
WORKDIR /app

RUN pip install -r requirements.txt

# RUN apt-get update && apt-get install -y fonts-dejavu

CMD ["python", "index.py"]
