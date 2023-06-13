package com.hescha.airparser;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionItem {
    private List<Clsn> clsn1List = new ArrayList<>();
    private List<Clsn> clsn2List = new ArrayList<>();
    private int spriteX;
    private int spriteY;
    private int offsetX;
    private int offsetY;
    private int time;
    private String flip;
    private String colorChanging;
    private Double xScale, yScale;
    private Integer rotateAngle;
    private boolean isFilled;
}
