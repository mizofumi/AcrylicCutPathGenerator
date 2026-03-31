import cv2
import numpy as np
import svgwrite
import sys
from skimage import measure
import base64
from io import BytesIO
from PIL import Image


def bezier_curve(points, num_points=100):
    """Given control points, generate a Bezier curve."""
    n = len(points) - 1

    def bernstein_poly(i, n, t):
        return scipy.special.comb(n, i) * (t ** i) * ((1 - t) ** (n - i))

    def bezier_interp(t):
        x = sum(bernstein_poly(i, n, t) * points[i][0] for i in range(n + 1))
        y = sum(bernstein_poly(i, n, t) * points[i][1] for i in range(n + 1))
        return (x, y)

    ts = np.linspace(0, 1, num_points)
    return [bezier_interp(t) for t in ts]


def contour_to_bezier_path(cnt, steps=20):
    """Convert a contour to a smooth path using cubic Bezier segments."""
    cnt = cnt.squeeze()
    if len(cnt.shape) != 2 or cnt.shape[0] < 4:
        return None  # skip if too few points

    path_data = []
    n = len(cnt)
    for i in range(0, n, 3):
        p0 = cnt[i % n]
        p1 = cnt[(i + 1) % n]
        p2 = cnt[(i + 2) % n]
        p3 = cnt[(i + 3) % n]
        if i == 0:
            path_data.append(f"M {p0[0]},{p0[1]}")
        path_data.append(f"C {p1[0]},{p1[1]} {p2[0]},{p2[1]} {p3[0]},{p3[1]}")
    path_data.append("Z")
    return " ".join(path_data)


def generate_cutpath(input_png, output_svg, offset=10, smooth=True, eps=0.001):
    import scipy.special  # lazy import
    image = cv2.imread(input_png, cv2.IMREAD_UNCHANGED)
    if image is None:
        raise FileNotFoundError(f"Image '{input_png}' not found.")

    height, width = image.shape[:2]

    if image.shape[2] == 4:
        alpha = image[:, :, 3]
        _, binary = cv2.threshold(alpha, 1, 255, cv2.THRESH_BINARY)
    else:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        _, binary = cv2.threshold(gray, 1, 255, cv2.THRESH_BINARY)

    contours = measure.find_contours(binary, 0.5)

    mask = np.zeros(binary.shape, dtype=np.uint8)
    for contour in contours:
        pts = np.flip(np.round(contour).astype(np.int32), axis=1)
        cv2.drawContours(mask, [pts], -1, 255, thickness=cv2.FILLED)

    # マスクをオフセット分パディングして、境界近くの輪郭がクリップされず画像サイズを超えて拡張されるようにする
    padded = cv2.copyMakeBorder(mask, offset, offset, offset, offset,
                                cv2.BORDER_CONSTANT, value=0)
    kernel_size = offset * 2 + 1
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    dilated = cv2.dilate(padded, kernel, iterations=1)

    final_contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    canvas_w = width + 2 * offset
    canvas_h = height + 2 * offset
    dwg = svgwrite.Drawing(output_svg, size=(f"{canvas_w}px", f"{canvas_h}px"))

    with Image.open(input_png) as img:
        buffered = BytesIO()
        img.save(buffered, format="PNG")
        img_b64 = base64.b64encode(buffered.getvalue()).decode()

    image_href = f"data:image/png;base64,{img_b64}"
    dwg.add(dwg.image(href=image_href, insert=(offset, offset), size=(f"{width}px", f"{height}px")))

    for cnt in final_contours:
        if smooth:
            path_data = contour_to_bezier_path(cnt)
            if path_data:
                dwg.add(dwg.path(d=path_data, fill="none", stroke="red", stroke_width=1))
        else:
            path_data = "M " + " L ".join(f"{p[0][0]},{p[0][1]}" for p in cnt) + " Z"
            dwg.add(dwg.path(d=path_data, fill="none", stroke="red", stroke_width=1))

    dwg.save()
    print(f"SVG saved to {output_svg}")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("使い方: python generate_acrylic_cutpath.py <入力画像.png> <出力パス.svg> [オフセット(px)] [smooth: true/false]")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]
    offset = int(sys.argv[3]) if len(sys.argv) > 3 else 10
    smooth = sys.argv[4].lower() != "false" if len(sys.argv) > 4 else True

    generate_cutpath(input_file, output_file, offset, smooth)

