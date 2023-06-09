package com.hescha.airparser;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionItem {
    List<Clsn> clsn1List = new ArrayList<>();
    List<Clsn> clsn2List = new ArrayList<>();
    int spriteX, spriteY, offsetX, offsetY, time;
    String flip;
    String colorChanging;
    Double xScale, yScale;
    Integer rotateAngle;
    boolean isFilled;
}
