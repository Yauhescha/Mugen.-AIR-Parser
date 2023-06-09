package com.hescha.airparser;

import java.util.ArrayList;
import java.util.List;

public class AirActionItem {
    List<Clsn> clsn1List = new ArrayList<>();
    List<Clsn> clsn2List = new ArrayList<>();
    int x;
    int y;
    int offsetX;
    int offsetY;
    int time;

    FlipType flipType = FlipType.NONE;
}
