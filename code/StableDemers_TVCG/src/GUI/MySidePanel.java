package GUI;

import Main.Settings;
import Model.CartogramModel;
import Model.Evaluation;
import nl.tue.geometrycore.gui.sidepanel.SideTab;
import nl.tue.geometrycore.gui.sidepanel.TabbedSidePanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Arrays;

public class MySidePanel extends TabbedSidePanel {
    public boolean[] visible_layers;
    public boolean show_obst;
    public boolean show_input;
    public boolean show_input_bb;
    public boolean show_debug;
    public boolean show_adj;
    public boolean show_adj_graph;
    public boolean show_dominance;
    public boolean show_x_dominance;
    public boolean show_y_dominance;
    public boolean show_last_forces;
    public boolean show_last_force_clicked;

    public boolean animate;
    public int[] anim_layers;
    public long animation_start_time;
    public int move_time = 1500;
    public int stand_time = 1000;
    public boolean show_color_grid;


    public MySidePanel(int layer_count, DrawPanel dp) {

        this.visible_layers = new boolean[layer_count];
        Arrays.fill(visible_layers, false);
        visible_layers[0] = true;
        anim_layers = new int[layer_count];
        Arrays.fill(anim_layers, -1);

        SideTab layer_tab = addTab("layers");

        for (int layer = 0; layer < layer_count; layer++) {
            int finalI = layer;
            layer_tab.addCheckbox("Layer "+layer, visible_layers[layer], (actionEvent, aBoolean) -> {
                visible_layers[finalI] = aBoolean;
                dp.repaint();
            });
        }

        show_obst = false;
        layer_tab.addSeparator();
        layer_tab.addCheckbox("Obstacles", show_obst, (actionEvent, aBoolean) -> {
            show_obst = aBoolean;
            dp.repaint();
        });

        layer_tab.addSeparator();

        show_input = false;
        layer_tab.addCheckbox("Input", show_input, (actionEvent, aBoolean) -> {
            show_input = aBoolean;
            dp.repaint();
        });

        show_color_grid = false;
        layer_tab.addCheckbox("Color grid", show_color_grid, (actionEvent, aBoolean) -> {
            show_color_grid = aBoolean;
            dp.repaint();
        });

        show_input_bb = false;
        layer_tab.addCheckbox("Input Bounding Box", show_input_bb, (actionEvent, aBoolean) -> {
            show_input_bb = aBoolean;
            dp.repaint();
        });

        layer_tab.addSeparator();

        show_adj = false;
        layer_tab.addCheckbox("Clicked Adjacencies", show_adj, (actionEvent, aBoolean) -> {
            show_adj = aBoolean;
            dp.repaint();
        });

        show_dominance = false;
        layer_tab.addCheckbox("Clicked Dominance", show_dominance, (actionEvent, aBoolean) -> {
            show_dominance = aBoolean;
            dp.repaint();
        });

        layer_tab.addSeparator();

        show_adj_graph = false;
        layer_tab.addCheckbox("Adjacency Graph", show_adj_graph, (actionEvent, aBoolean) -> {
            show_adj_graph = aBoolean;
            dp.repaint();
        });

        show_x_dominance = false;
        layer_tab.addCheckbox("X-Dominance Graph", show_x_dominance, (actionEvent, aBoolean) -> {
            show_x_dominance = aBoolean;
            dp.repaint();
        });

        show_y_dominance = false;
        layer_tab.addCheckbox("Y-Dominance Graph", show_y_dominance, (actionEvent, aBoolean) -> {
            show_y_dominance = aBoolean;
            dp.repaint();
        });

        layer_tab.addSeparator();

        show_last_forces = false;
        layer_tab.addCheckbox("All Forces", show_last_forces, (actionEvent, aBoolean) -> {
            show_last_forces = aBoolean;
            dp.repaint();
        });

        show_last_force_clicked = false;
        layer_tab.addCheckbox("Clicked Force", show_last_force_clicked, (actionEvent, aBoolean) -> {
            show_last_force_clicked = aBoolean;
            dp.repaint();
        });

        layer_tab.addSeparator();

        for (int i = 0; i < layer_count; i++) {
            layer_tab.addLabel("Lost Adj in layer " + i + ": "+ CartogramModel.lost_adj_for_drawing.get(i));
            if(CartogramModel.lost_adj_for_drawing_ILP != null){
                layer_tab.addLabel("Lost ILP in layer " + i + ": "+ CartogramModel.lost_adj_for_drawing_ILP.get(i));
            }
            layer_tab.addSpace();
        }

        layer_tab.addSeparator();

        if(Settings.solution_method.equals("LP")){
            if(Settings.LP.double_disjont_c) {
                layer_tab.addLabel("Double Disjoint");
            }
            if(Settings.LP.distance_minimization_c) {
                layer_tab.addLabel("Distance: " + Settings.LP.factor_dist_min);
            }
            if(Settings.LP.angle_nuance_c) {
                layer_tab.addLabel("Angle: " + Settings.LP.factor_angle_nuance);
            }
            if(Settings.LP.origin_displacement_c) {
                layer_tab.addLabel("Origin: " + Settings.LP.factor_orig_displ);
            }
            if(Settings.LP.topology_c) {
                layer_tab.addLabel("Topology: " + Settings.LP.factor_topology);
            }
            if(Settings.LP.stability_c) {
                layer_tab.addLabel("Stability: " + Settings.LP.factor_stability);
                layer_tab.addLabel("\tModel: " + Settings.stability_path);
            }
        }

        if(Settings.solution_method.equals("Force")){
            if(Settings.Force.disjoint_f) {
                layer_tab.addLabel("Disjoint: " + Settings.Force.disjoint_weight);
            }
            if(Settings.Force.origin_f) {
                layer_tab.addLabel("Origin: " + Settings.Force.origin_weight);
            }
            if(Settings.Force.topological_f) {
                layer_tab.addLabel("Topo: " + Settings.Force.topological_weight);
            }
            if(Settings.Force.stability_f) {
                layer_tab.addLabel("Stability: " + Settings.Force.stability_weight);
            }
        }

        layer_tab.addSeparator();

        animate = false;
        layer_tab.addButton("Animate", (actionEvent) -> {
            animate = !animate;
            if(animate)
                animation_start_time = System.currentTimeMillis();
            dp.repaint();
        });

//        for (int i = 0; i < layer_count; i++) {
//            if(i%4 == 0)
//                layer_tab.makeCustomSplit(1, .25, .25, .25, 25);
//            JButton k = layer_tab.addButton("" + i, null);
//            k.addMouseListener(new MouseAdapter() {
//                @Override
//                public void mouseClicked(MouseEvent e) {
//                    super.mouseClicked(e);
//                    if(e.getButton() == 1) {
//                        anim_layer_1 = Integer.parseInt(k.getText());
//                    }
//                    if(e.getButton() == 3) {
//                        anim_layer_2 = Integer.parseInt(k.getText());
//                    }
//                    System.out.println("Animation from " + anim_layer_1 + " to " + anim_layer_2);
//                }
//            });
//        }

        for (int i = 0; i < layer_count; i++) {
            anim_layers[i] = i;
//            if(i%4 == 0)
//                layer_tab.makeCustomSplit(1, .25, .25, .25, 25);
            JTextField k = layer_tab.addTextField(""+i);

            int finalI = i;
            k.getDocument().addDocumentListener(new DocumentListener() {
                public void changedUpdate(DocumentEvent e) {
                    anim_layers[finalI] = Integer.parseInt(k.getText());
                    animation_start_time = System.currentTimeMillis();
                }
                public void removeUpdate(DocumentEvent e) {
                    anim_layers[finalI] = -1;
                    animation_start_time = System.currentTimeMillis();
                }
                public void insertUpdate(DocumentEvent e) {
                    anim_layers[finalI] = Integer.parseInt(k.getText());
                    animation_start_time = System.currentTimeMillis();
                }
            });
        }

        layer_tab.addSeparator();

        layer_tab.makeCustomSplit((int) 2, .5, .5);
        layer_tab.addLabel("Speed");
        JTextField speed = layer_tab.addTextField("3");
        speed.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                move_time = Integer.parseInt(speed.getText())*500;
                animation_start_time = System.currentTimeMillis();
            }
            public void removeUpdate(DocumentEvent e) {
                move_time = 3000;
                animation_start_time = System.currentTimeMillis();
            }
            public void insertUpdate(DocumentEvent e) {
                move_time = Integer.parseInt(speed.getText())*500;
                animation_start_time = System.currentTimeMillis();
            }
        });


//        JSlider slide = layer_tab.addIntegerSlider(3, 1, 10, (changeEvent, integer) -> move_time = integer * 1000);
//        slide.setSnapToTicks(true);
//        slide.updateUI();

//        show_debug = false;
//        layer_tab.addCheckbox("DEBUG", show_debug, (actionEvent, aBoolean) -> {
//            show_debug = aBoolean;
//            dp.repaint();
//        });
    }

    public boolean is_visible(int layer) {
        return visible_layers[layer];
    }
}
