package sff;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class Shower extends JFrame {


    public Shower(BufferedImage bufferedImage) {
        super("Shower image"); //Название программы
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);//Что бы она закрывалась
        setSize(600, 250); //размер программы
        ImageIcon ii = new ImageIcon(bufferedImage);
        ii.getImage();
        setContentPane(new JLabel(new ImageIcon(ii.getImage().getScaledInstance(width, height, Image.SCALE_DEFAULT)))); //Размер рисунка


        //Создаём лейбл
        JLabel label = new JLabel("");
        label.setBounds(0, 0, width, height); //Размер и позиция лейбла (x и y - координаты width и height ширина и высота)
        Container container = this.getContentPane(); //Добавляем контейнер
        container.add(label);
        this.setVisible(true);
    }
}
