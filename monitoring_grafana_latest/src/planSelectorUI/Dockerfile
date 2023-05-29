FROM python:3.9-bullseye
WORKDIR /app
COPY . /app
RUN pip install -r requirements.txt
ENV FLASK_APP=plan_selector_ui
EXPOSE 5003
CMD waitress-serve --port=5003 --host=0.0.0.0 --call 'plan_selector_ui:create_app'
