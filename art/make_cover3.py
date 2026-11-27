
import math, random
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont

FONT_B="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
CY=(80,225,255); CY2=(160,248,255)

def lerp(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
def radial(size,inner=(13,18,24),outer=(3,5,7)):
    w,h=size; img=Image.new("RGB",(w,h)); px=img.load(); cx,cy=w/2,h/2; maxd=math.hypot(cx,cy)
    for y in range(h):
        for x in range(w):
            d=math.hypot(x-cx,y-cy)/maxd
            px[x,y]=lerp(inner,outer,min(1.0,(d**0.85)))
    return img
def grid(img,step,alpha=10):
    d=ImageDraw.Draw(img,"RGBA"); w,h=img.size
    for x in range(0,w,step): d.line([(x,0),(x,h)],fill=CY+(alpha,))
    for y in range(0,h,step): d.line([(0,y),(w,y)],fill=CY+(alpha,))
def glow(size,fn,blur):
    lay=Image.new("RGBA",size,(0,0,0,0)); fn(ImageDraw.Draw(lay,"RGBA"))
    return lay.filter(ImageFilter.GaussianBlur(blur))

def polys(cx,cy,w,hh,sh):
    return ([(cx,cy-hh),(cx+w,cy),(cx,cy+hh),(cx-w,cy)],
            [(cx-w,cy),(cx,cy+hh),(cx,cy+hh+sh),(cx-w,cy+sh)],
            [(cx+w,cy),(cx,cy+hh),(cx,cy+hh+sh),(cx+w,cy+sh)])

def face_grid(p0,p1,p2,p3,cols,rows):
    for r in range(rows):
        for c in range(cols):
            u0,u1=c/cols,(c+1)/cols; v0,v1=r/rows,(r+1)/rows
            def pt(u,v):
                ax=p0[0]+(p1[0]-p0[0])*u; ay=p0[1]+(p1[1]-p0[1])*u
                bx=p3[0]+(p2[0]-p3[0])*u; by=p3[1]+(p2[1]-p3[1])*u
                return (ax+(bx-ax)*v, ay+(by-ay)*v)
            yield [pt(u0,v0),pt(u1,v0),pt(u1,v1),pt(u0,v1)]

def block_layer(size,cx,cy,w,hh,sh,S,tint=1.0):
    lay=Image.new("RGBA",size,(0,0,0,0)); d=ImageDraw.Draw(lay,"RGBA")
    top,left,right=polys(cx,cy,w,hh,sh)
    def c(rgb): return tuple(min(255,int(v*tint)) for v in rgb)
    d.polygon(top,fill=c((66,80,94,255)))
    d.polygon(left,fill=c((50,62,74,255)))
    d.polygon(right,fill=c((37,47,57,255)))
    lw=max(1,int(2.0*S))
    for q in face_grid(*left,3,3): d.polygon(q,fill=c((56,69,82,255)),outline=(84,205,230,150),width=lw)
    for q in face_grid(*right,3,3): d.polygon(q,fill=c((42,53,64,255)),outline=(84,205,230,110),width=lw)
    for sc,a in [(0.70,160),(0.40,100)]:
        t=[(cx,cy-hh*sc),(cx+w*sc,cy),(cx,cy+hh*sc),(cx-w*sc,cy)]
        d.polygon(t,outline=(120,232,252,a),width=max(1,int(2.4*S)))
    d.ellipse([cx-w*0.12,cy+hh*0.38,cx+w*0.12,cy+hh*0.38+w*0.24],outline=(130,240,255,170),width=max(1,int(2.6*S)))
    for p in (top,left,right): d.line(p+[p[0]],fill=(110,226,248,190),width=max(1,int(2.4*S)))
    return lay

def crack_pts(cx,y0,y1,amp,seed):
    rnd=random.Random(seed); pts=[(cx,y0)]; y=y0; a=max(1,int(amp))
    while y<y1:
        y+=rnd.randint(12,26); pts.append((cx+rnd.randint(-a,a),min(y,y1)))
    return pts

def split_block(size,cx,cy,w,hh,sh,S,shift,seed=9,amp=0.014):
    blk=block_layer(size,cx,cy,w,hh,sh,S)
    W,H=size
    mask=Image.new("L",size,0)
    ImageDraw.Draw(mask).polygon([(cx+1,0),(W,0),(W,H),(cx+1,H)],fill=255)
    r=Image.new("RGBA",size,(0,0,0,0)); r.paste(blk,(int(shift),0))
    out=Image.new("RGBA",size,(0,0,0,0))
    out.paste(blk,(0,0),ImageChops.invert(mask)); out.paste(r,(0,0),mask)
    pts=crack_pts(cx,cy-hh*0.95,cy+hh+sh*1.00,W*amp,seed)
    def seam(d):
        d.line(pts,fill=(235,253,255,255),width=max(1,int(3.0*S)),joint="curve")
    out=Image.alpha_composite(out,glow(size,seam,int(W*0.03)))
    out=Image.alpha_composite(out,glow(size,seam,int(W*0.010)))
    out=Image.alpha_composite(out,glow(size,seam,max(1,int(1.5*S))))
    return out

def icon(path,size=512,ss=3):
    W=size*ss; img=radial((W,W)).convert("RGBA"); grid(img,int(W/16),9)
    img=Image.alpha_composite(img,glow((W,W),lambda d:d.ellipse([W*0.12,W*0.06,W*0.88,W*0.82],fill=CY+(38,)),int(W*0.06)))
    w,hh,sh=W*0.285,W*0.148,W*0.285; cx,cy=W*0.475,W*0.375
    blk=block_layer((W,W),cx,cy,w,hh,sh,ss)
    gap=int(W*0.030)
    mask=Image.new("L",(W,W),0)
    ImageDraw.Draw(mask).polygon([(cx+1,0),(W,0),(W,W),(cx+1,W)],fill=255)
    lhalf=Image.new("RGBA",(W,W),(0,0,0,0)); lhalf.paste(blk,(-gap//2,-int(W*0.008)))
    rhalf=Image.new("RGBA",(W,W),(0,0,0,0)); rhalf.paste(blk,(gap-gap//2,int(W*0.010)))
    out=Image.new("RGBA",(W,W),(0,0,0,0))
    out.paste(lhalf,(0,0),ImageChops.invert(mask)); out.paste(rhalf,(0,0),mask)
    pts=crack_pts(cx,cy-hh*0.95,cy+hh+sh*1.0,max(1,int(W*0.002)),9)
    def seam(d): d.line(pts,fill=(238,253,255,255),width=max(1,int(3.4*ss)),joint="curve")
    out=Image.alpha_composite(out,glow((W,W),seam,int(W*0.030)))
    out=Image.alpha_composite(out,glow((W,W),seam,int(W*0.009)))
    out=Image.alpha_composite(out,glow((W,W),seam,max(1,int(1.6*ss))))
    img=Image.alpha_composite(img,out)
    # 漂浮的小碎片（子 CPU）
    fw,fhh,fsh=w*0.26,hh*0.26,sh*0.26
    fx,fy=cx+w*1.55,cy-hh*0.95
    frag=block_layer((W,W),fx,fy,fw,fhh,fsh,ss,tint=0.95).filter(ImageFilter.GaussianBlur(0))
    frag_glow=glow((W,W),lambda d:d.ellipse([fx-fw,fy-fhh,fx+fw,fy+fsh+fhh],fill=CY+(45,)),int(W*0.012))
    img=Image.alpha_composite(img,frag_glow)
    img=Image.alpha_composite(img,frag)
    mini=crack_pts(fx,fy-fhh*0.9,fy+fhh+fsh*0.95,W*0.004,4)
    def mini_seam(d): d.line(mini,fill=(230,252,255,255),width=max(1,int(1.6*ss)),joint="curve")
    img=Image.alpha_composite(img,glow((W,W),mini_seam,int(W*0.008)))
    # 火花
    rnd=random.Random(21)
    def sparks(d):
        for _ in range(50):
            x=cx+rnd.uniform(-w*1.3,w*1.35); y=cy+rnd.uniform(-hh*1.2,hh+sh*1.02)
            rr=rnd.uniform(W*0.0012,W*0.0032)
            d.ellipse([x-rr,y-rr,x+rr,y+rr],fill=CY2+(rnd.randint(80,200),))
    img=Image.alpha_composite(img,glow((W,W),sparks,int(W*0.004)))
    img=img.resize((size,size),Image.LANCZOS).convert("RGB"); img.save(path,"PNG"); return path

def banner(path,W=1280,H=640,ss=2):
    S=(W*ss,H*ss); img=radial(S).convert("RGBA"); grid(img,int(S[0]/30),8)
    img=Image.alpha_composite(img,glow(S,lambda d:d.ellipse([0,S[1]*0.08,S[0]*0.52,S[1]*0.98],fill=CY+(34,)),int(S[0]*0.05)))
    w,hh,sh=S[0]*0.108,S[0]*0.056,S[0]*0.108; cx,cy=S[0]*0.195,S[1]*0.42
    blk=block_layer(S,cx,cy,w,hh,sh,ss); gap=int(S[0]*0.008)
    mask=Image.new("L",S,0); ImageDraw.Draw(mask).polygon([(cx+1,0),(S[0],0),(S[0],S[1]),(cx+1,S[1])],fill=255)
    lh=Image.new("RGBA",S,(0,0,0,0)); lh.paste(blk,(-gap//2,-int(S[0]*0.003)))
    rh=Image.new("RGBA",S,(0,0,0,0)); rh.paste(blk,(gap-gap//2,int(S[0]*0.004)))
    out=Image.new("RGBA",S,(0,0,0,0)); out.paste(lh,(0,0),ImageChops.invert(mask)); out.paste(rh,(0,0),mask)
    pts=crack_pts(cx,cy-hh*0.95,cy+hh+sh*1.0,max(1,int(S[0]*0.0015)),9)
    def seam(d): d.line(pts,fill=(238,253,255,255),width=max(1,int(3.0*ss)),joint="curve")
    out=Image.alpha_composite(out,glow(S,seam,int(S[0]*0.012)))
    out=Image.alpha_composite(out,glow(S,seam,int(S[0]*0.004)))
    img=Image.alpha_composite(img,out)
    title="AE2 SPLIT CPU"; sub="One CPU. Many jobs at once."; foot="Forge 1.20.1    ·    NeoForge 1.21.1"
    x0=int(S[0]*0.355); maxw=int(S[0]*0.965)-x0
    size=int(104*ss)
    while size>20:
        fb=ImageFont.truetype(FONT_B,size)
        if ImageDraw.Draw(Image.new("RGBA",(10,10))).textlength(title,font=fb)<=maxw: break
        size-=2
    fb=ImageFont.truetype(FONT_B,size)
    fs=ImageFont.truetype(FONT_B,int(size*0.33)); ft=ImageFont.truetype(FONT_B,int(size*0.22))
    th=size; sh_=int(size*0.33); fh=ft.size
    total=th+int(size*0.10)+sh_+int(size*0.30)+fh
    y=int((S[1]-total)/2)
    head=Image.new("RGBA",S,(0,0,0,0)); ImageDraw.Draw(head).text((x0,y),title,font=fb,fill=CY+(150,))
    img=Image.alpha_composite(img,head.filter(ImageFilter.GaussianBlur(int(7*ss))))
    txt=Image.new("RGBA",S,(0,0,0,0)); d=ImageDraw.Draw(txt)
    d.text((x0,y),title,font=fb,fill=(242,253,255,255))
    d.text((x0,y+th+int(size*0.10)),sub,font=fs,fill=(150,240,255,255))
    d.text((x0,y+th+int(size*0.10)+sh_+int(size*0.30)),foot,font=ft,fill=(140,160,170,255))
    img=Image.alpha_composite(img,txt)
    img=img.resize((W,H),Image.LANCZOS).convert("RGB"); img.save(path,"PNG"); return path

icon("/root/java-mod-dev/cover/logo-512.png",512,3)
icon("/root/java-mod-dev/cover/logo-256.png",256,4)
icon("/root/java-mod-dev/cover/logo-400.png",400,4)
banner("/root/java-mod-dev/cover/banner-1280x640.png")
print("done")

