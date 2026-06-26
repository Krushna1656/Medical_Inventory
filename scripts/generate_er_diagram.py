from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


BASE_WIDTH = 1152
BASE_HEIGHT = 768
SCALE = 2
CANVAS_SIZE = (BASE_WIDTH * SCALE, BASE_HEIGHT * SCALE)
BACKGROUND = "white"
FOREGROUND = "black"
LINE_WIDTH = 2 * SCALE


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    font_candidates = []
    if bold:
        font_candidates.extend(
            [
                Path(r"C:\Windows\Fonts\arialbd.ttf"),
                Path(r"C:\Windows\Fonts\segoeuib.ttf"),
            ]
        )
    else:
        font_candidates.extend(
            [
                Path(r"C:\Windows\Fonts\arial.ttf"),
                Path(r"C:\Windows\Fonts\segoeui.ttf"),
            ]
        )

    for candidate in font_candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)

    return ImageFont.load_default()


def s(value: int) -> int:
    return value * SCALE


def draw_centered_text(draw: ImageDraw.ImageDraw, center: tuple[int, int], text: str, font) -> None:
    bbox = draw.textbbox((0, 0), text, font=font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    draw.text((center[0] - text_width / 2, center[1] - text_height / 2), text, fill=FOREGROUND, font=font)


def draw_entity(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], label: str, font) -> None:
    scaled = tuple(s(value) for value in box)
    draw.rounded_rectangle(scaled, radius=s(8), outline=FOREGROUND, width=LINE_WIDTH, fill=BACKGROUND)
    center = ((scaled[0] + scaled[2]) // 2, (scaled[1] + scaled[3]) // 2)
    draw_centered_text(draw, center, label, font)


def draw_attribute(
    draw: ImageDraw.ImageDraw,
    center: tuple[int, int],
    size: tuple[int, int],
    label: str,
    font,
) -> None:
    cx, cy = s(center[0]), s(center[1])
    width, height = s(size[0]), s(size[1])
    bounds = (cx - width // 2, cy - height // 2, cx + width // 2, cy + height // 2)
    draw.ellipse(bounds, outline=FOREGROUND, width=LINE_WIDTH, fill=BACKGROUND)
    draw_centered_text(draw, (cx, cy), label, font)


def draw_diamond(
    draw: ImageDraw.ImageDraw,
    center: tuple[int, int],
    size: tuple[int, int],
    label: str,
    font,
) -> None:
    cx, cy = s(center[0]), s(center[1])
    width, height = s(size[0]), s(size[1])
    points = [
        (cx, cy - height // 2),
        (cx + width // 2, cy),
        (cx, cy + height // 2),
        (cx - width // 2, cy),
    ]
    draw.polygon(points, outline=FOREGROUND, width=LINE_WIDTH, fill=BACKGROUND)
    draw_centered_text(draw, (cx, cy), label, font)


def draw_line(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]]) -> None:
    scaled_points = [(s(x), s(y)) for x, y in points]
    draw.line(scaled_points, fill=FOREGROUND, width=LINE_WIDTH)


def draw_cardinality(draw: ImageDraw.ImageDraw, point: tuple[int, int], label: str, font) -> None:
    draw_centered_text(draw, (s(point[0]), s(point[1])), label, font)


def rect_center(box: tuple[int, int, int, int]) -> tuple[float, float]:
    return ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2)


def rect_boundary_point(box: tuple[int, int, int, int], target: tuple[float, float]) -> tuple[float, float]:
    cx, cy = rect_center(box)
    dx = target[0] - cx
    dy = target[1] - cy
    half_width = (box[2] - box[0]) / 2
    half_height = (box[3] - box[1]) / 2

    if dx == 0 and dy == 0:
        return (cx, cy)

    scale = max(abs(dx) / half_width if half_width else 0, abs(dy) / half_height if half_height else 0)
    return (cx + dx / scale, cy + dy / scale)


def ellipse_boundary_point(
    center: tuple[int, int],
    size: tuple[int, int],
    target: tuple[float, float],
) -> tuple[float, float]:
    cx, cy = center
    dx = target[0] - cx
    dy = target[1] - cy
    rx = size[0] / 2
    ry = size[1] / 2

    if dx == 0 and dy == 0:
        return (cx, cy)

    scale = ((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry)) ** 0.5
    return (cx + dx / scale, cy + dy / scale)


def draw_attribute_connector(
    draw: ImageDraw.ImageDraw,
    attr_center: tuple[int, int],
    attr_size: tuple[int, int],
    entity_box: tuple[int, int, int, int],
) -> None:
    start = ellipse_boundary_point(attr_center, attr_size, rect_center(entity_box))
    end = rect_boundary_point(entity_box, attr_center)
    draw_line(
        draw,
        [
            (round(start[0]), round(start[1])),
            (round(end[0]), round(end[1])),
        ],
    )


def main() -> None:
    image = Image.new("RGB", CANVAS_SIZE, BACKGROUND)
    draw = ImageDraw.Draw(image)

    entity_font = load_font(s(18), bold=True)
    attribute_font = load_font(s(15))
    relationship_font = load_font(s(15))
    cardinality_font = load_font(s(17))

    admin_box = (150, 270, 375, 315)
    medicine_box = (615, 115, 825, 165)
    stock_box = (595, 355, 845, 405)

    draw_entity(draw, admin_box, "ADMIN USERS", entity_font)
    draw_entity(draw, medicine_box, "MEDICINES", entity_font)
    draw_entity(draw, stock_box, "STOCK MOVEMENTS", entity_font)

    admin_id_center, admin_id_size = (235, 183), (165, 55)
    admin_created_center, admin_created_size = (100, 235), (165, 55)
    admin_username_center, admin_username_size = (360, 235), (165, 55)
    admin_full_name_center, admin_full_name_size = (110, 350), (165, 55)
    admin_password_center, admin_password_size = (325, 382), (200, 58)

    draw_attribute(draw, admin_id_center, admin_id_size, "id(PK)", attribute_font)
    draw_attribute(draw, admin_created_center, admin_created_size, "created_at", attribute_font)
    draw_attribute(draw, admin_username_center, admin_username_size, "username", attribute_font)
    draw_attribute(draw, admin_full_name_center, admin_full_name_size, "full_name", attribute_font)
    draw_attribute(draw, admin_password_center, admin_password_size, "password_hash", attribute_font)

    draw_attribute_connector(draw, admin_id_center, admin_id_size, admin_box)
    draw_attribute_connector(draw, admin_created_center, admin_created_size, admin_box)
    draw_attribute_connector(draw, admin_username_center, admin_username_size, admin_box)
    draw_attribute_connector(draw, admin_full_name_center, admin_full_name_size, admin_box)
    draw_attribute_connector(draw, admin_password_center, admin_password_size, admin_box)

    medicine_id_center, medicine_id_size = (495, 62), (145, 55)
    medicine_name_center, medicine_name_size = (645, 62), (150, 55)
    medicine_quantity_center, medicine_quantity_size = (810, 62), (160, 55)
    medicine_price_center, medicine_price_size = (975, 62), (145, 55)
    medicine_expiry_center, medicine_expiry_size = (445, 172), (195, 55)
    medicine_created_center, medicine_created_size = (965, 172), (175, 55)

    draw_attribute(draw, medicine_id_center, medicine_id_size, "id(PK)", attribute_font)
    draw_attribute(draw, medicine_name_center, medicine_name_size, "name", attribute_font)
    draw_attribute(draw, medicine_quantity_center, medicine_quantity_size, "quantity", attribute_font)
    draw_attribute(draw, medicine_price_center, medicine_price_size, "price", attribute_font)
    draw_attribute(draw, medicine_expiry_center, medicine_expiry_size, "expiry_date", attribute_font)
    draw_attribute(draw, medicine_created_center, medicine_created_size, "created_at", attribute_font)

    draw_attribute_connector(draw, medicine_id_center, medicine_id_size, medicine_box)
    draw_attribute_connector(draw, medicine_name_center, medicine_name_size, medicine_box)
    draw_attribute_connector(draw, medicine_quantity_center, medicine_quantity_size, medicine_box)
    draw_attribute_connector(draw, medicine_price_center, medicine_price_size, medicine_box)
    draw_attribute_connector(draw, medicine_expiry_center, medicine_expiry_size, medicine_box)
    draw_attribute_connector(draw, medicine_created_center, medicine_created_size, medicine_box)

    stock_id_center, stock_id_size = (600, 456), (150, 55)
    stock_type_center, stock_type_size = (770, 460), (130, 55)
    stock_reference_center, stock_reference_size = (945, 452), (185, 55)
    stock_note_center, stock_note_size = (1020, 392), (145, 55)
    stock_created_center, stock_created_size = (1015, 312), (175, 55)

    draw_attribute(draw, stock_id_center, stock_id_size, "id(PK)", attribute_font)
    draw_attribute(draw, stock_type_center, stock_type_size, "type", attribute_font)
    draw_attribute(draw, stock_reference_center, stock_reference_size, "reference_no", attribute_font)
    draw_attribute(draw, stock_note_center, stock_note_size, "note", attribute_font)
    draw_attribute(draw, stock_created_center, stock_created_size, "created_at", attribute_font)

    draw_attribute_connector(draw, stock_id_center, stock_id_size, stock_box)
    draw_attribute_connector(draw, stock_type_center, stock_type_size, stock_box)
    draw_attribute_connector(draw, stock_reference_center, stock_reference_size, stock_box)
    draw_attribute_connector(draw, stock_note_center, stock_note_size, stock_box)
    draw_attribute_connector(draw, stock_created_center, stock_created_size, stock_box)

    draw_diamond(draw, (500, 340), (92, 68), "manages", relationship_font)
    draw_line(draw, [(375, 293), (454, 340)])
    draw_line(draw, [(546, 340), (595, 378)])
    draw_cardinality(draw, (394, 296), "1", cardinality_font)
    draw_cardinality(draw, (585, 347), "N", cardinality_font)

    draw_diamond(draw, (720, 270), (82, 70), "has", relationship_font)
    draw_line(draw, [(720, 165), (720, 235)])
    draw_line(draw, [(720, 305), (720, 355)])
    draw_cardinality(draw, (700, 190), "1", cardinality_font)
    draw_cardinality(draw, (700, 316), "N", cardinality_font)

    draw_diamond(draw, (515, 245), (92, 68), "manages", relationship_font)
    draw_line(draw, [(375, 300), (469, 245)])
    draw_line(draw, [(561, 245), (615, 135)])
    draw_cardinality(draw, (430, 258), "1", cardinality_font)
    draw_cardinality(draw, (594, 145), "N", cardinality_font)

    final_image = image.resize((BASE_WIDTH, BASE_HEIGHT), Image.Resampling.LANCZOS)

    project_root = Path(__file__).resolve().parents[1]
    output_dir = project_root / "All diagrams"
    output_dir.mkdir(parents=True, exist_ok=True)

    png_path = output_dir / "ER.png"
    jpeg_path = output_dir / "ER.jpeg"

    final_image.save(png_path)
    final_image.save(jpeg_path, quality=95)

    print(f"Saved {png_path}")
    print(f"Saved {jpeg_path}")


if __name__ == "__main__":
    main()
