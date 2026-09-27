package com.vaultchain.service.impl;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Port of image-hash 5.3.2 block-hash method 2, used by the source backend with 16 bits. */
public final class BlockHash {
    private BlockHash() {}
    public static String hash(BufferedImage image) {
        int bits=16,width=image.getWidth(),height=image.getHeight();
        if(width<1||height<1)throw new IllegalArgumentException("Invalid image");
        double[] blocks=new double[bits*bits];
        boolean evenX=width%bits==0,evenY=height%bits==0;
        if(evenX&&evenY){
            int blockX=width/bits,blockY=height/bits;
            for(int y=0;y<bits;y++)for(int x=0;x<bits;x++){
                double total=0;
                for(int iy=0;iy<blockY;iy++)for(int ix=0;ix<blockX;ix++)total+=value(image.getRGB(x*blockX+ix,y*blockY+iy));
                blocks[y*bits+x]=total;
            }
            return encode(blocks,blockX*blockY);
        }
        double blockWidth=(double)width/bits,blockHeight=(double)height/bits;
        for(int y=0;y<height;y++){
            int top,bottom;double weightTop,weightBottom;
            if(evenY){bottom=(int)Math.floor(y/blockHeight);top=bottom;weightTop=1;weightBottom=0;}
            else{double mod=(y+1)%blockHeight,frac=mod-Math.floor(mod),integer=mod-frac;
                weightTop=1-frac;weightBottom=frac;
                if(integer>0||y+1==height){bottom=(int)Math.floor(y/blockHeight);top=bottom;}
                else{top=(int)Math.floor(y/blockHeight);bottom=(int)Math.ceil(y/blockHeight);}}
            for(int x=0;x<width;x++){
                int left,right;double weightLeft,weightRight;
                if(evenX){right=(int)Math.floor(x/blockWidth);left=right;weightLeft=1;weightRight=0;}
                else{double mod=(x+1)%blockWidth,frac=mod-Math.floor(mod),integer=mod-frac;
                    weightLeft=1-frac;weightRight=frac;
                    if(integer>0||x+1==width){right=(int)Math.floor(x/blockWidth);left=right;}
                    else{left=(int)Math.floor(x/blockWidth);right=(int)Math.ceil(x/blockWidth);}}
                double pixel=value(image.getRGB(x,y));
                blocks[top*bits+left]+=pixel*weightTop*weightLeft;
                blocks[top*bits+right]+=pixel*weightTop*weightRight;
                blocks[bottom*bits+left]+=pixel*weightBottom*weightLeft;
                blocks[bottom*bits+right]+=pixel*weightBottom*weightRight;
            }
        }
        return encode(blocks,blockWidth*blockHeight);
    }
    private static int value(int argb){return ((argb>>>24)&255)==0?765:((argb>>>16)&255)+((argb>>>8)&255)+(argb&255);}
    private static String encode(double[] blocks,double pixelsPerBlock){
        int[] binary=new int[blocks.length];int band=blocks.length/4;
        for(int i=0;i<4;i++){
            double[] ordered=Arrays.copyOfRange(blocks,i*band,(i+1)*band);Arrays.sort(ordered);
            double median=(ordered[band/2]+ordered[band/2+1])/2.0;
            double half=pixelsPerBlock*256*3/2.0;
            for(int j=i*band;j<(i+1)*band;j++){double v=blocks[j];binary[j]=v>median||Math.abs(v-median)<1&&median>half?1:0;}
        }
        StringBuilder hex=new StringBuilder(binary.length/4);
        for(int i=0;i<binary.length;i+=4)hex.append(Character.forDigit(binary[i]*8+binary[i+1]*4+binary[i+2]*2+binary[i+3],16));
        return hex.toString();
    }
    public static int distance(String left,String right){
        if(left==null||right==null||!left.matches("(?i)[0-9a-f]+")||!right.matches("(?i)[0-9a-f]+"))
            throw new IllegalArgumentException("Hashes must be hexadecimal");
        if(left.length()!=right.length())throw new IllegalArgumentException("Hashes must have equal lengths");
        int sum=0;for(int i=0;i<left.length();i++)sum+=Integer.bitCount(Character.digit(left.charAt(i),16)^Character.digit(right.charAt(i),16));
        return sum;
    }
}
