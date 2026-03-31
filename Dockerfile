FROM ubuntu:24.04

RUN apt update -y && apt install -y libgl1 python3-pip
RUN pip install opencv-python numpy svgwrite scikit-image --break-system-packages
RUN pip install fastapi uvicorn python-multipart opencv-python-headless pillow svgwrite numpy scikit-image --break-system-packages
ADD . .

ENTRYPOINT ["uvicorn", "main:app", "--reload", "--host", "0.0.0.0", "--port", "8000" ]
