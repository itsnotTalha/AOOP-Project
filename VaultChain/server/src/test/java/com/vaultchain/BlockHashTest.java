package com.vaultchain;

import static org.assertj.core.api.Assertions.assertThat;
import com.vaultchain.service.impl.BlockHash;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BlockHashTest {
    @Test void matchesOriginalImageHashPackageForUnevenTransparentPng() {
        BufferedImage image=new BufferedImage(37,29,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<29;y++)for(int x=0;x<37;x++){
            int red=(x*13+y*7)%256,green=(x*3+y*19)%256,blue=(x*y*11)%256,alpha=(x+y)%9==0?0:255;
            image.setRGB(x,y,(alpha<<24)|(red<<16)|(green<<8)|blue);
        }
        assertThat(BlockHash.hash(image)).isEqualTo("010113272f6f4e7f1ebeff917c222024444c18b8b1b9737bf7f4efe0e8a00121");
        assertThat(BlockHash.distance("00","03")).isEqualTo(2);
    }
}
