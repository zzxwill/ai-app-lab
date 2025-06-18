# Use Microsoft's Python base image with stable Ubuntu
FROM mcr.microsoft.com/playwright/python:v1.52.0-noble
# FROM  hub.byted.org/lab/playwright-python:v1.50.0-noble

WORKDIR /app

COPY requirements.txt .

RUN pip install -r requirements.txt

COPY . .

# RUN apt-get update && apt-get install -y fonts-dejavu

CMD ["python", "index.py"]
