from math import atan2, cos, sin
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


BASE_WIDTH = 1498
BASE_HEIGHT = 694
SCALE = 2
CANVAS_SIZE = (BASE_WIDTH * SCALE, BASE_HEIGHT * SCALE)
BACKGROUND = "white"
FOREGROUND = "black"
LINE_WIDTH = 2 * SCALE


def s(value: int) -> int:
    return value * SCALE


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = []
    if bold:
        candidates.extend(
            [
                Path(r"C:\Windows\Fonts\arialbd.ttf"),
                Path(r"C:\Windows\Fonts\segoeuib.ttf"),
            ]
        )
    else:
        candidates.extend(
            [
                Path(r"C:\Windows\Fonts\arial.ttf"),
                Path(r"C:\Windows\Fonts\segoeui.ttf"),
            ]
        )

    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)

    return ImageFont.load_default()


def draw_centered_text(draw: ImageDraw.ImageDraw, center: tuple[int, int], text: str, font) -> None:
    bbox = draw.textbbox((0, 0), text, font=font)
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    draw.text((center[0] - width / 2, center[1] - height / 2), text, fill=FOREGROUND, font=font)


def draw_centered_multiline(draw: ImageDraw.ImageDraw, center: tuple[int, int], text: str, font) -> None:
    bbox = draw.multiline_textbbox((0, 0), text, font=font, spacing=s(8), align="center")
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    draw.multiline_text(
        (center[0] - width / 2, center[1] - height / 2),
        text,
        fill=FOREGROUND,
        font=font,
        spacing=s(8),
        align="center",
    )


def draw_rounded_use_case(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], label: str, font) -> None:
    scaled = tuple(s(value) for value in box)
    radius = min((scaled[2] - scaled[0]) // 4, (scaled[3] - scaled[1]) // 2)
    draw.rounded_rectangle(scaled, radius=radius, outline=FOREGROUND, width=LINE_WIDTH, fill=BACKGROUND)
    center = ((scaled[0] + scaled[2]) // 2, (scaled[1] + scaled[3]) // 2)
    draw_centered_multiline(draw, center, label, font)


def left_mid(box: tuple[int, int, int, int]) -> tuple[int, int]:
    return (box[0], (box[1] + box[3]) // 2)


def right_mid(box: tuple[int, int, int, int]) -> tuple[int, int]:
    return (box[2], (box[1] + box[3]) // 2)


def left_point(box: tuple[int, int, int, int], offset_from_top: int) -> tuple[int, int]:
    return (box[0], box[1] + offset_from_top)


def draw_solid_polyline(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]]) -> None:
    scaled = [(s(x), s(y)) for x, y in points]
    draw.line(scaled, fill=FOREGROUND, width=LINE_WIDTH)


def draw_dashed_arrow(draw: ImageDraw.ImageDraw, start: tuple[int, int], end: tuple[int, int]) -> None:
    x1, y1 = s(start[0]), s(start[1])
    x2, y2 = s(end[0]), s(end[1])
    dx = x2 - x1
    dy = y2 - y1
    length = (dx * dx + dy * dy) ** 0.5
    if length == 0:
        return

    dash = s(10)
    gap = s(7)
    arrow_len = s(14)
    distance = 0
    while distance + dash < length - arrow_len - s(2):
        segment_start = distance / length
        segment_end = (distance + dash) / length
        sx = x1 + dx * segment_start
        sy = y1 + dy * segment_start
        ex = x1 + dx * segment_end
        ey = y1 + dy * segment_end
        draw.line([(sx, sy), (ex, ey)], fill=FOREGROUND, width=LINE_WIDTH)
        distance += dash + gap

    angle = atan2(dy, dx)
    arrow_angle = 0.42
    left = (
        x2 - arrow_len * cos(angle - arrow_angle),
        y2 - arrow_len * sin(angle - arrow_angle),
    )
    right = (
        x2 - arrow_len * cos(angle + arrow_angle),
        y2 - arrow_len * sin(angle + arrow_angle),
    )
    draw.polygon([(x2, y2), left, right], fill=FOREGROUND)


def draw_label_with_background(draw: ImageDraw.ImageDraw, center: tuple[int, int], text: str, font) -> None:
    bbox = draw.textbbox((0, 0), text, font=font)
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    x = center[0] - width / 2
    y = center[1] - height / 2
    padding_x = s(10)
    padding_y = s(6)
    draw.rectangle(
        (
            x - padding_x,
            y - padding_y,
            x + width + padding_x,
            y + height + padding_y,
        ),
        fill=BACKGROUND,
    )
    draw.text((x, y), text, fill=FOREGROUND, font=font)


def draw_actor(draw: ImageDraw.ImageDraw, center_x: int, top_y: int, font) -> None:
    cx = s(center_x)
    top = s(top_y)
    head_r = s(30)
    head_center_y = top + head_r
    draw.ellipse(
        (cx - head_r, head_center_y - head_r, cx + head_r, head_center_y + head_r),
        outline=FOREGROUND,
        width=LINE_WIDTH,
        fill=BACKGROUND,
    )

    neck_y = head_center_y + head_r
    hip_y = neck_y + s(95)
    arm_y = neck_y + s(34)
    leg_y = hip_y + s(90)

    draw.line([(cx, neck_y), (cx, hip_y)], fill=FOREGROUND, width=LINE_WIDTH)
    draw.line([(cx - s(56), arm_y), (cx + s(56), arm_y)], fill=FOREGROUND, width=LINE_WIDTH)
    draw.line([(cx, hip_y), (cx - s(42), leg_y)], fill=FOREGROUND, width=LINE_WIDTH)
    draw.line([(cx, hip_y), (cx + s(42), leg_y)], fill=FOREGROUND, width=LINE_WIDTH)

    draw_centered_text(draw, (cx, leg_y + s(42)), "Admin User", font)


def main() -> None:
    image = Image.new("RGB", CANVAS_SIZE, BACKGROUND)
    draw = ImageDraw.Draw(image)

    title_font = load_font(s(25))
    use_case_font = load_font(s(24))
    actor_font = load_font(s(22))
    relation_font = load_font(s(21))

    system_rect = (360, 15, 1450, 671)
    draw.rectangle(tuple(s(value) for value in system_rect), outline=FOREGROUND, width=LINE_WIDTH)
    draw_centered_text(draw, (s(905), s(39)), "MediCore Hospital Admin System", title_font)

    access_box = (450, 80, 700, 154)
    monitor_box = (420, 240, 780, 370)
    reports_box = (420, 470, 780, 600)
    inventory_box = (1035, 240, 1385, 370)

    draw_rounded_use_case(draw, access_box, "Access System", use_case_font)
    draw_rounded_use_case(draw, monitor_box, "Monitor Dashboard\n& Alerts", use_case_font)
    draw_rounded_use_case(draw, reports_box, "Generate Reports\n& Update Settings", use_case_font)
    draw_rounded_use_case(draw, inventory_box, "Manage Inventory\n& Stock", use_case_font)

    draw_actor(draw, center_x=120, top_y=55, font=actor_font)

    actor_access = (176, 149)
    actor_monitor = (176, 182)
    actor_reports = (145, 225)
    actor_inventory = (160, 155)

    draw_solid_polyline(draw, [actor_access, (360, 149), left_mid(access_box)])
    draw_solid_polyline(draw, [actor_monitor, (360, 305), left_mid(monitor_box)])
    draw_solid_polyline(draw, [actor_reports, (360, 535), left_mid(reports_box)])
    inventory_top_connection = (inventory_box[0] + 80, inventory_box[1])
    draw_solid_polyline(draw, [actor_inventory, (395, 200), (inventory_top_connection[0], 200), inventory_top_connection])

    draw_dashed_arrow(draw, right_mid(monitor_box), left_point(inventory_box, 60))
    draw_dashed_arrow(draw, (reports_box[2], reports_box[1] + 48), left_point(inventory_box, 110))

    draw_label_with_background(draw, (s(905), s(310)), "reads", relation_font)
    draw_label_with_background(draw, (s(890), s(538)), "uses inventory data", relation_font)

    final_image = image.resize((BASE_WIDTH, BASE_HEIGHT), Image.Resampling.LANCZOS)

    project_root = Path(__file__).resolve().parents[1]
    output_path = project_root / "All diagrams" / "use-case-diagram.png"
    final_image.save(output_path)
    print(f"Saved {output_path}")


if __name__ == "__main__":
    main()
