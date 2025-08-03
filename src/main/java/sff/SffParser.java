package sff;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parser for M.U.G.E.N sprite archives.<br/>
 * Handles both legacy <b>SFF v1.01</b> and modern <b>SFF v2</b> containers.
 * <p>Standalone – depends only on JDK + Lombok.</p>
 */
public final class SffParser {

    private static final String SIG_V1 = "ElecbyteSpr\0";   // v1 header, 12 bytes incl. NUL
    private static final String SIG_V2 = "ElecbyteSFF2";    // v2 header, 12 bytes (no NUL)

    private static final int HDR_SIZE    = 512;
    private static final int SUB_V1_SIZE = 32;
    private static final int SUB_V2_SIZE = 32;

    private SffParser() { }

    // ------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------

    /** Parses the supplied SFF file. */
    public static @NonNull SffFile parse(@NonNull Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length < HDR_SIZE) throw new SffParseException("File too small to be SFF");

        String sig = new String(data, 0, 12, StandardCharsets.US_ASCII);
        if (SIG_V1.equals(sig)) return parseV1(data);
        if (SIG_V2.equals(sig)) return parseV2(data);
        throw new SffParseException("Unknown signature: " + sig);
    }

    /** Decodes a sprite (follows links, supports PNG & 8‑bit PCX). */
    public static BufferedImage toImage(@NonNull SffFile file, @NonNull Sprite spr) throws IOException {
        if (spr.isLinked()) spr = file.getSprites().get(spr.getPrevCopy());
        byte[] raw = spr.getData();
        if (raw == null) throw new IOException("Sprite has no data");
        if (isPng(raw)) return ImageIO.read(new ByteArrayInputStream(raw));
        if (isPcx(raw)) return Pcx.decode(raw);
        throw new IOException("Unsupported sprite format");
    }

    // ------------------------------------------------------------
    // v1.01 implementation
    // ------------------------------------------------------------

    private static SffFile parseV1(byte[] all) {
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        int groups = bb.getInt(16);
        int images = bb.getInt(20);
        int first  = bb.getInt(24);
        int subSz  = bb.getInt(28);
        if (subSz == 0) subSz = SUB_V1_SIZE; // tolerant — some tools write 0
        if (subSz != SUB_V1_SIZE) throw new SffParseException("Unexpected v1 subheader size: " + subSz);
        if (first == 0 && images > 0) first = HDR_SIZE; // heuristic: firstSub missing → assume after header
        boolean sharedPal = (bb.get(32) & 0xFF) == 1;
        return build(all, groups, images, first, subSz, sharedPal, false);//(all, groups, images, first, subSz, sharedPal, false);
    }

    // ------------------------------------------------------------
    // v2 implementation (MUGEN 1.1)
    // ------------------------------------------------------------

    private static SffFile parseV2(byte[] all) {
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        int groups = bb.getInt(16);
        int images = bb.getInt(20);
        int first  = bb.getInt(24);
        int subSz  = bb.getInt(28);
        if (subSz != SUB_V2_SIZE) throw new SffParseException("Unexpected v2 subheader size: " + subSz);
        boolean sharedPal = (bb.get(32) & 0xFF) == 1;
        return build(all, groups, images, first, subSz, sharedPal, true);
    }

    // ------------------------------------------------------------
    // Common builder logic
    // ------------------------------------------------------------

    private static SffFile build(byte[] all, int groups, int images, int off, int subSize, boolean sharedPal, boolean v2) {
        List<Sprite> list = new ArrayList<>(images);
        Map<Integer, List<Sprite>> byGroup = new LinkedHashMap<>();

        int idx = 0;
        while (off != 0 && idx < images) {
            if (off + subSize > all.length) throw new SffParseException("Subheader OOB @" + off);
            int next   = leInt(all, off);
            int length = leInt(all, off + 4);
            int axisX  = leShort(all, off + 8);
            int axisY  = leShort(all, off + 10);
            int group  = v2 ? leInt(all, off + 12) : (leShort(all, off + 12) & 0xFFFF);
            int number = v2 ? leInt(all, off + 16) : (leShort(all, off + 14) & 0xFFFF);
            int prev   = leShort(all, off + (v2 ? 18 : 16)) & 0xFFFF;
            int fmt    = all[off + (v2 ? 20 : 18)] & 0xFF; // v1: samePal flag; v2: format 0/3/4

            boolean linked = length == 0;
            byte[] payload = null;
            if (!linked) {
                int start = off + subSize;
                if (start + length > all.length) throw new SffParseException("Data OOB @ sprite " + idx);
                payload = Arrays.copyOfRange(all, start, start + length);
            }

            Sprite s = Sprite.builder()
                    .index(idx).group(group).number(number)
                    .axisX(axisX).axisY(axisY)
                    .linked(linked).prevCopy(prev)
                    .format(fmt).data(payload)
                    .build();
            list.add(s);
            byGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(s);

            off = next; idx++;
        }
        if (list.size() != images) throw new SffParseException("Parsed " + list.size() + " sprites, header says " + images);
        return SffFile.builder()
                .groups(groups).images(images).sharedPalette(sharedPal)
                .sprites(Collections.unmodifiableList(list))
                .byGroup(Collections.unmodifiableMap(byGroup))
                .build();
    }

    // ------------------------------------------------------------
    // LE helpers & file‑format probes
    // ------------------------------------------------------------

    private static int  leInt  (byte[] d,int p){return (d[p]&0xFF)|((d[p+1]&0xFF)<<8)|((d[p+2]&0xFF)<<16)|((d[p+3]&0xFF)<<24);}
    static short leShort(byte[] d, int p){return (short)((d[p]&0xFF)|((d[p+1]&0xFF)<<8));}

    private static boolean isPng(byte[] b){return b.length>=8 && b[0]==(byte)0x89 && b[1]==0x50 && b[2]==0x4E && b[3]==0x47;}
    private static boolean isPcx(byte[] b){return b.length>=4 && (b[0]&0xFF)==0x0A;}


// -----------------------------------------------------------------------------
// Model classes (package‑private)
// -----------------------------------------------------------------------------

    @Data
    @Builder
    public static class SffFile {
        int groups, images; boolean sharedPalette;
        @NonNull List<Sprite> sprites; @NonNull Map<Integer, List<Sprite>> byGroup;
        Sprite get(int g,int n){List<Sprite>l=byGroup.get(g);return l==null?null:l.stream().filter(s->s.number==n).findFirst().orElse(null);} }

    @Data
    @Builder
    public static class Sprite {
        int index, group, number;
        int axisX, axisY;
        boolean linked; int prevCopy; int format; byte[] data; }

// -----------------------------------------------------------------------------
// 8‑bit PCX decoder (minimal)
// -----------------------------------------------------------------------------

    public static final class Pcx {
        static BufferedImage decode(byte[] pcx) throws IOException {
            if ((pcx[0] & 0xFF) != 0x0A) throw new IOException("Not PCX");
            int xmin = SffParser.leShort(pcx,4), ymin = SffParser.leShort(pcx,6);
            int xmax = SffParser.leShort(pcx,8), ymax = SffParser.leShort(pcx,10);
            int w = xmax - xmin + 1, h = ymax - ymin + 1;
            int palettePos = pcx.length - 769;
            if (palettePos < 128 || (pcx[palettePos] & 0xFF) != 0x0C)
                throw new IOException("PCX palette missing");
            byte[] palBytes = Arrays.copyOfRange(pcx, palettePos + 1, palettePos + 769);
            int[] cmap = new int[256];
            for (int i = 0; i < 256; i++) {
                int r = palBytes[i*3]&0xFF, g = palBytes[i*3+1]&0xFF, b = palBytes[i*3+2]&0xFF;
                cmap[i] = 0xFF000000 | (r<<16) | (g<<8) | b;
            }
            IndexColorModel icm = new IndexColorModel(8,256,cmap,0,false,-1, DataBuffer.TYPE_BYTE);
            byte[] pixels = decodeRle(pcx,128,palettePos,w*h);
            BufferedImage img = new BufferedImage(w,h,BufferedImage.TYPE_BYTE_INDEXED,icm);
            img.getRaster().setDataElements(0,0,w,h,pixels);
            return img;
        }
        private static byte[] decodeRle(byte[] src,int from,int to,int expected){
            byte[] out = new byte[expected]; int idx=0;
            for(int p=from;p<to && idx<expected;){
                int b = src[p++] & 0xFF;
                if((b & 0xC0)==0xC0){int cnt=b&0x3F; if(cnt==0) cnt=1; int val=src[p++] & 0xFF; Arrays.fill(out, idx, Math.min(idx+cnt, expected), (byte)val); idx += cnt;}
                else out[idx++] = (byte) b;
            }
            if(idx < expected) throw new ArrayIndexOutOfBoundsException("Decoded "+idx+" < expected "+expected);
            return out;
        }
    }

// -----------------------------------------------------------------------------
// Exception
// -----------------------------------------------------------------------------

    static class SffParseException extends RuntimeException { SffParseException(String m){super(m);} }

}
