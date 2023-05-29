FROM python:3.9-bullseye
WORKDIR /app
COPY . /app
RUN pip install -r requirements.txt
ENV FLASK_APP=route_calculator
EXPOSE 7501
CMD waitress-serve --port=7501 --call 'route_calculator:create_app' 