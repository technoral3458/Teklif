"""
DWD / IngoMachine XML dışa aktarıcı (Aşama E).

Patlat + donanım delikli panelleri, altı-kenar delme makinesinin okuduğu
IngoMachine XML'ine yazar (en baştaki 123.xml formatı).

Eşleme:
  op 'v' (yüze dikey)  -> Type 2, Face 5
  op 'h' (kenara yatay)-> Type 1, Face 1/2/3/4 (on/arka/sag/sol)
  op 'groove' (freze)  -> Type 3, Face 6, Lines

Panel düzlemi x=0..L -> DWD X (Length), y=0..W -> DWD Y (Width).
"""

FACE_EDGE = {"on": 1, "arka": 2, "sag": 3, "sol": 4}
FACE_TOP = 5
FACE_BOTTOM = 6


def _esc(v):
    return (str(v).replace("&", "&amp;").replace('"', "&quot;")
            .replace("<", "&lt;").replace(">", "&gt;"))


def _fmt(v):
    f = float(v)
    return str(int(f)) if f.is_integer() else f"{f:.3f}"


def build_dwd(project_name, panels, order_no=""):
    """panels = hardware.drill_panels(...)['panels']  -> DWD XML metni."""
    out = ['<?xml version="1.0"?>']
    out.append('<Root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
               'xmlns:xsd="http://www.w3.org/2001/XMLSchema" '
               'Application="IngoMachine" ApplicationVersion="2.0">')
    out.append(f'  <Project Name="{_esc(project_name)}">')
    out.append('    <Panels>')

    pid_base = 202600000000
    for pi, p in enumerate(panels):
        L = float(p["L"]); W = float(p["W"]); t = float(p["t"])
        pid = pid_base + pi + 1
        out.append(
            f'      <Panel IsProduce="true" ID="{pid}" Type="1" MachiningPoint="1" '
            f'OrderNo="{_esc(order_no)}" Name="{_esc(p["name"])}" Thickness="{_fmt(t)}" '
            f'Qty="{int(p["qty"])}" CutLength="{_fmt(L)}" CutWidth="{_fmt(W)}" '
            f'Length="{_fmt(L)}" Width="{_fmt(W)}" Face5ID="{pid}" Face6ID="">')
        out.append('        <Machines>')

        mid = 100000 + 1
        for op in p.get("ops", []):
            z = _fmt(t / 2)
            if op["kind"] == "v":
                out.append(
                    f'          <Machining IsGenCode="2" X="{_fmt(op["x"])}" Y="{_fmt(op["y"])}" '
                    f'Z="{z}" Face="{FACE_TOP}" Depth="{_fmt(op["depth"])}" '
                    f'Diameter="{_fmt(op["d"])}" ID="{mid}" Type="2" />')
            elif op["kind"] == "h":
                face = FACE_EDGE.get(op["face"], 1)
                # kenar deliğinde DWD X/Y, kenara göre kenar üzerindeki konumdur
                out.append(
                    f'          <Machining IsGenCode="2" X="{_fmt(op["x"])}" Y="{_fmt(op["y"])}" '
                    f'Z="{z}" Face="{face}" Depth="{_fmt(op["depth"])}" '
                    f'Diameter="{_fmt(op["d"])}" ID="{mid}" Type="1" />')
            elif op["kind"] == "groove":
                out.append(
                    f'          <Machining IsGenCode="2" EdgeABThickness="0" X="{_fmt(op["x"])}" '
                    f'Y="{_fmt(op["y"])}" StartZ="0" Depth="{_fmt(op["depth"])}" ZDepth="0" '
                    f'ToolOffset="" ToolName="xxd{int(op["d"])}" Face="{FACE_BOTTOM}" Type="3" ID="{mid}">')
                out.append('            <Lines>')
                out.append(f'              <Line Angle="0" UnitTag="铣型" '
                           f'EndX="{_fmt(op["x2"])}" EndY="{_fmt(op["y2"])}" LineID="1" />')
                out.append('            </Lines>')
                out.append('          </Machining>')
            mid += 1

        out.append('        </Machines>')
        out.append('        <EdgeGroup X1="0" Y1="0">')
        for f in (1, 2, 3, 4):
            out.append(f'          <Edge Face="{f}" Thickness="0" Pre_Milling="0" X="0" Y="0" CentralAngle="0" />')
        out.append('        </EdgeGroup>')
        out.append('      </Panel>')

    out.append('    </Panels>')
    out.append('  </Project>')
    out.append('</Root>')
    return "\n".join(out)
