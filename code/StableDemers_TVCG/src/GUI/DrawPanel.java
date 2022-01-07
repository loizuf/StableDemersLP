package GUI;

import Force.ForceSolver;
import Main.Settings;
import Model.CartogramModel;
import Model.Evaluation;
import Model.Region;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometryrendering.GeometryPanel;
import nl.tue.geometrycore.geometryrendering.glyphs.ArrowStyle;
import nl.tue.geometrycore.geometryrendering.styling.Dashing;
import nl.tue.geometrycore.geometryrendering.styling.FontStyle;
import nl.tue.geometrycore.geometryrendering.styling.TextAnchor;
import nl.tue.geometrycore.io.ipe.IPEWriter;
import nl.tue.geometrycore.util.DoubleUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class DrawPanel extends GeometryPanel {

    CartogramModel c;
    private MySidePanel sp;

    // ONLY FOR DEBUGGING - DELETE THIS AT SOME POINT
    private int clicked_region_index = -1;
    private boolean on_start = true;

    public DrawPanel(CartogramModel cartogram) {
        super();
        this.c = cartogram;
    }

    public void setSP(MySidePanel sp) {
        this.sp = sp;
    }

    @Override
    protected void drawScene() {

        IPEWriter write = null;
        if (getRenderer() instanceof IPEWriter) {
            write = (IPEWriter) getRenderer();
        }

        if (write != null) {
//            write.setLayer("cartogram");
            write.setAlpha(1.0D);
            write.setStroke(Color.white, 1, null);
            write.setTextStyle(TextAnchor.CENTER, 56, FontStyle.NORMAL);
        } else {
            setAlpha(0.6D);
        }

        // TODO: Is this necessary?
        //setSizeMode(SizeMode.VIEW);
        if(sp.show_color_grid){
            setStroke(null, 0, null);
            double bb_w = c.getInputBoundingBox().width();
            double bb_h = c.getInputBoundingBox().height();
            for (int x = 0; x < bb_w; x+=2) {
                for (int y = 0; y < bb_h; y+=2) {
                    setFill(new Color((float) (1 - ((x/(2*bb_w)) + y/(2*bb_h))), (float) (x/bb_w), (float) (y/bb_h)), null);
                    draw(new Circle(new Vector(x, y), 1));
                }
            }
        }

        for (int layer = 0; layer < c.getLayerCount(); layer++) {
            if(!sp.is_visible(layer)) continue;
            List<Region> l_regions = c.getRegions(layer);
            double colour_fraction = (((double) layer) / ((double) c.getLayerCount() - 1));

            if(sp.show_input_bb) {
                setFill(null, null);
                setStroke(Color.RED, 1, null);
                draw(c.getInputBoundingBox());
            }



            for (Region l_region : l_regions) {
//                System.out.println(l_region.getPosition(layer) + " when drawing " + l_region.code);
                if(write != null) {
                    write.setLayer("regions");
                    setTextStyle(TextAnchor.CENTER, 56, FontStyle.NORMAL);
                }

                setStroke(Color.darkGray, 1, null);
                // Obstacles in red, regions in blue
                if(sp.show_input){
                    setStroke(Color.gray, 1, null);
                    setFill(null, null);
//                    draw(l_region.getInputPolygon());
                    draw(l_region.getInputBoundingBox());
                    if(sp.show_adj_graph || sp.show_dominance){
                        setFill(Color.lightGray, null);
                    } else {
                        setFill(l_region.color, null);
                    }
                    setStroke(Color.gray, 1, null);
                    draw(new Circle(l_region.original_location, 3));
                    setFill(null, null);
                } else if (sp.show_debug) {
                    setFill(Color.BLACK, null);
                    setStroke(Color.BLACK, 1, null);
                    setTextStyle(TextAnchor.CENTER, 80);
                    draw(getView().center(), "USE THIS FOR DEBUG PURPOSES");

//                    double layer_min, layer_max;
//                    layer_min = c.getMinSize(layer);
//                    layer_max = c.getMaxSize(layer);
//                    double buffer = Math.min(layer_min, (layer_max-layer_min)/10);

//                    if (l_region.code.equals("CA") || l_region.code.equals("CT")) {
//                        setStroke(Color.darkGray, 1, null);
//                        setFill(Color.darkGray, null);
//                        draw(Rectangle.byCenterAndSize(l_region.original_location, l_region.getSize(layer), l_region.getSize(layer)));
//                        setStroke(Color.gray, 1, null);
//                        setFill(Color.gray, null);
//                        draw(Rectangle.byCenterAndSize(l_region.original_location, l_region.getSize(layer) + buffer, l_region.getSize(layer) + buffer));
//                        setFill(null, null);
//                    }
                } else {
                    if(l_region.isObstacle) {
                        if(write != null){
                            write.setLayer("obstacles");
                        }
                        if(sp.show_obst||write != null){
                            setStroke(null, 1, null);
                            setFill(Color.gray, null);
                            draw(l_region.getRectangle(layer));
                        }
                    } else {
                        setStroke(null, 1, null);
                        if(sp.show_adj_graph || sp.show_dominance){
                            setFill(Color.lightGray, null);
                        } else {
                            setFill(l_region.color, null);
                        }
//                        setFill(new Color(0, (int) (colour_fraction * 166), (int) (colour_fraction * 255)), null);
                        Rectangle region_rect = l_region.getRectangle(layer);


                        //This is a good template to visualize regions without data, which was interpolated?
//                        if (region_rect.width()<=.5) {
//                            region_rect.setWidth(.5);
//                            region_rect.setHeight(.5);
//                            setStroke(Color.black, .1, Dashing.dashed(.1));
//                            setFill(null, null);
//                        }


                        draw(region_rect);
                        if(sp.show_adj_graph || sp.show_dominance) {
                            setFill(Color.BLACK, null);
                            draw(new Circle(l_region.getPosition(layer), 3));
                        }
                    }
                    if(l_region.getSize(layer) > getWorldview().width()*0.04){
                        // Text in white
                        //System.out.println(l_region.getSize(layer) - getWorldview().width()*0.03);
                        setStroke(Color.WHITE, 1, null);
                        if(write != null) {
                            if(l_region.isObstacle) {
                                write.setLayer("obstacle_labels");
                            } else {
                                write.setLayer("labels");
                            }
                            setTextStyle(TextAnchor.CENTER, l_region.getSize(layer), FontStyle.NORMAL);
                        } else {
                            setTextStyle(TextAnchor.CENTER, l_region.getSize(layer)/10 + getWorldview().width()*0.015, FontStyle.NORMAL);
                        }
                        draw(l_region.getPosition(layer), l_region.code);
                    }
                }
            }
        }

        setAlpha(1D);

        if(sp.show_adj_graph) {
            // This is debugging code for visualizig the domination and separation constraints
            setForwardArrowStyle(null, .2);
            for (int layer = 0; layer < c.getLayerCount(); layer++) {
                if (!sp.is_visible(layer)) continue;
                List<Region> l_regions = c.getRegions(layer);

                for (Region l_region : l_regions) {

                    for (Region l_region_2 : l_regions) {
                        if (c.adjacent(l_region, l_region_2, layer)) {
                            //if (c.secondary_separated(l_region, l_region_2, 'x')) {
                            if(Evaluation.adjKept(l_region, l_region_2, layer)){
                                setStroke(new Color(229, 172, 116, 255), 0.4, null);
                            } else {
                                setStroke(new Color(208, 0, 45, 255), 0.8, null);
                            }
                            draw(new LineSegment(l_region.getPosition(layer), l_region_2.getPosition(layer)));
                        }
                    }
                }
            }
        }

        if(sp.show_x_dominance){
            setForwardArrowStyle(ArrowStyle.TRIANGLE_SOLID, 1);
            for (int layer = 0; layer < c.getLayerCount(); layer++) {
                if (!sp.is_visible(layer)) continue;
                List<Region> l_regions = c.getRegions(layer);

                for (Region l_region : l_regions) {
                    for (Region l_region_2 : l_regions) {
                        if (c.dominates(l_region_2, l_region, layer, 'x')) {
                            //if (c.secondary_separated(l_region, l_region_2, 'x')) {
                            setStroke(new Color(96, 157, 217), .2, null);
                            draw(new LineSegment(l_region.getPosition(layer), l_region_2.getPosition(layer)));

                        }
                    }
                }
            }
        }

        if(sp.show_y_dominance){
            setForwardArrowStyle(ArrowStyle.TRIANGLE_SOLID, 1);
            for (int layer = 0; layer < c.getLayerCount(); layer++) {
                if (!sp.is_visible(layer)) continue;
                List<Region> l_regions = c.getRegions(layer);

                for (Region l_region : l_regions) {
                    for (Region l_region_2 : l_regions) {
                        if (c.dominates(l_region_2, l_region, layer, 'y')) {
                            //if (c.secondary_separated(l_region, l_region_2, 'y')) {
                            setStroke(new Color(163, 222, 134), .2, null);
                            draw(new LineSegment(l_region.getPosition(layer), l_region_2.getPosition(layer)));
                        }
                    }
                }
            }
        }

        if(sp.show_adj) {
            // This is debugging code for visualizig the domination and separation constraints
            setForwardArrowStyle(null, .2);
            for (int layer = 0; layer < c.getLayerCount(); layer++) {
                if(clicked_region_index == -1) break;
                if (!sp.is_visible(layer)) continue;
                List<Region> l_regions = c.getRegions(layer);

                for (Region l_region : l_regions) {

                    for (Region l_region_2 : l_regions) {
                        if (c.adjacent(l_region, l_region_2, layer)) {
                            //if (c.secondary_separated(l_region, l_region_2, 'x')) {
                            setStroke(new Color(241, 134, 21, 255), 0.1, null);
                            if (l_region.index == clicked_region_index) {
                                draw(new LineSegment(l_region.getPosition(layer), l_region_2.getPosition(layer)));
                            }
                        }
                    }
                }
            }
        }

        if(sp.show_dominance) {
            // This is debugging code for visualizig the domination and separation constraints
            setForwardArrowStyle(ArrowStyle.TRIANGLE_SOLID, .8);
            for (int layer = 0; layer < c.getLayerCount(); layer++) {
                if(clicked_region_index == -1) break;
                if (!sp.is_visible(layer)) continue;
                List<Region> l_regions = c.getRegions(layer);

                for (Region l_region : l_regions) {

                    for (Region l_region_2 : l_regions) {
                        if (c.dominates(l_region_2, l_region, layer, 'x')) {
                            //if (c.secondary_separated(l_region, l_region_2, 'x')) {
                            if (l_region.index == clicked_region_index) {
                                setStroke(new Color(31, 126, 208), .05, null);
                                draw(new LineSegment(l_region.getPosition(layer), l_region_2.getPosition(layer)));
                            }
                        }
                        if (c.dominates(l_region_2, l_region, layer, 'y')) {
                            //if (c.secondary_separated(l_region, l_region_2, 'y')) {
                            if (l_region.index == clicked_region_index) {
                                setStroke(new Color(79, 203, 22), .05, null);
                                draw(new LineSegment(l_region.getPosition(layer), l_region_2.getPosition(layer)));
                            }
                        }
                        if (c.dominates(l_region, l_region_2, layer, 'x')) {
                            //if (c.secondary_separated(l_region, l_region_2, 'x')) {
                            if (l_region.index == clicked_region_index) {
                                setStroke(new Color(31, 126, 208), .05, null);
                                draw(new LineSegment(l_region_2.getPosition(layer), l_region.getPosition(layer)));
                            }
                        }
                        if (c.dominates(l_region, l_region_2, layer, 'y')) {
                            //if (c.secondary_separated(l_region, l_region_2, 'y')) {
                            if (l_region.index == clicked_region_index) {
                                setStroke(new Color(79, 203, 22), .05, null);
                                draw(new LineSegment(l_region_2.getPosition(layer), l_region.getPosition(layer)));
                            }
                        }
                    }
                }
            }


            if(on_start){
                zoomToFit();
                on_start = false;
            }
        }

        if(sp.show_last_force_clicked) {
            setForwardArrowStyle(null, .8);
            for (int layer = 0; layer < c.getLayerCount(); layer++) {
                if(clicked_region_index == -1) break;
                if (!sp.is_visible(layer)) continue;

                for (Region l_region : c.getRegions(layer)) {
                    if (l_region.index != clicked_region_index) continue;


                    setStroke(new Color(78, 109, 68), .2, null);
                    List<Vector> vectors = ForceSolver.lastMapForces.get(layer).get(clicked_region_index);
                    if(vectors != null && vectors.size()>0) {
                        for (int i = 1; i < vectors.size(); i++) {
                            Vector force = vectors.get(i);
                            draw(new LineSegment(l_region.getPosition(layer), Vector.add(l_region.getPosition(layer), force)));
                        }
                        setStroke(new Color(79, 203, 22), .2, null);
                        draw(new LineSegment(l_region.getPosition(layer), Vector.add(l_region.getPosition(layer), vectors.get(0))));
                    }

                    setStroke(new Color(75, 98, 121), .2, null);
                    vectors = ForceSolver.lastDisjointForces.get(layer).get(clicked_region_index);
                    if(vectors != null && vectors.size()>0) {
                        for (int i = 1; i < vectors.size(); i++) {
                            Vector force = vectors.get(i);
                            draw(new LineSegment(l_region.getPosition(layer), Vector.add(l_region.getPosition(layer), force)));
                        }
                        setStroke(new Color(31, 126, 208), .2, null);
                        draw(new LineSegment(l_region.getPosition(layer), Vector.add(l_region.getPosition(layer), vectors.get(0))));
                    }

                    setStroke(Color.red, .2, null);
                    draw(new LineSegment(l_region.getPosition(layer), Vector.add(l_region.getPosition(layer), ForceSolver.lastTotalForces.get(layer).get(l_region.index))));
                }
            }
        }


        if (sp.animate) {

            int move_time = sp.move_time;
            int stand_time = sp.stand_time;
            int iteration_time = move_time + stand_time;
            int total_anim_time = -iteration_time;
            int number_anim_layers = 0;
//            Collection<Region> regions = c.getRegions();
            for (int i = 0; i < c.getLayerCount(); i++) {
//                System.out.println(sp.anim_layers[i]);
                if (sp.anim_layers[i] == -1) continue;
                number_anim_layers++;
                total_anim_time += iteration_time;
                //regions.retainAll(c.getRegions(sp.anim_layers[i]));
            }

            long time_measured = System.currentTimeMillis() - sp.animation_start_time;
            long time4000 = time_measured % iteration_time;
            double k;
            if (time4000<=move_time) {
                k = time4000 / ((double)(move_time));
            } else {
                k = 1;
            }
            if(total_anim_time>=iteration_time){
                // Timing for the animation from one cartogramm to the next

                long time_total = time_measured % (2*total_anim_time);
//                long time_one_way = time_measured % total_anim_time;
                int counter = (int) (time_total / (iteration_time));

//                System.out.println("----------------------");
//                System.out.println("total anim time: " + total_anim_time);
//                System.out.println("total: " + time_total);
////                System.out.println("one way: " + time_one_way);
//                System.out.println("number of layers: " + number_anim_layers);
//                System.out.println("counter: " + counter);
//                for (int i = 0; i < c.getLayerCount(); i++) {
//                    System.out.print(sp.anim_layers[i] + ", ");
//                }
//                System.out.println();
//                System.out.println("----------------------");

                int anim_layer_1, anim_layer_2;
                if(time_total < total_anim_time) {
                    anim_layer_1 = sp.anim_layers[counter];
                    anim_layer_2 = sp.anim_layers[counter+1];
                } else {
                    anim_layer_1 = sp.anim_layers[2*number_anim_layers - counter - 2];
                    anim_layer_2 = sp.anim_layers[2*number_anim_layers - counter - 3];
                }

                List<Region> regions;
                if (k == 1){
                    regions  = new ArrayList<>(c.getRegions(anim_layer_2));
                } else {
                    regions  = new ArrayList<>(c.getRegions(anim_layer_2));
                    regions.retainAll(c.getRegions(anim_layer_1));
                }

                double f = sigmoid(k*10 - 5);

                double colour_fraction_1 = (((double) anim_layer_1) / ((double) c.getLayerCount() - 1));
                double colour_fraction_2 = (((double) anim_layer_2) / ((double) c.getLayerCount() - 1));
                double colour_fraction = (1-f)*colour_fraction_1 + f*colour_fraction_2;

                for (Region region : regions) {
                    if(region.isObstacle) {
                        if(sp.show_obst){
                            setStroke(null, 1, null);
                            setFill(Color.red, null);

                            Vector c = Vector.add(Vector.multiply(1 - f, (region.getPosition(anim_layer_1))), Vector.multiply(f, region.getPosition(anim_layer_2)));
                            double w = (1 - f) * region.getSize(anim_layer_1) + f * region.getSize(anim_layer_2);
                            draw(Rectangle.byCenterAndSize(c, w, w));
                        }
                    } else {
                        setStroke(null, 1, null);
//                        setFill(new Color(0, (int) (colour_fraction * 166), (int) (colour_fraction * 255)), null);
                        setFill(region.color, null);

                        Vector c = Vector.add(Vector.multiply(1 - f, (region.getPosition(anim_layer_1))), Vector.multiply(f, region.getPosition(anim_layer_2)));
                        double w = (1 - f) * region.getSize(anim_layer_1) + f * region.getSize(anim_layer_2);
                        draw(Rectangle.byCenterAndSize(c, w, w));

                        if(w > getWorldview().width()*0.04){
                            // Text in white
                            //System.out.println(l_region.getSize(layer) - getWorldview().width()*0.03);
                            setStroke(Color.WHITE, 1, null);
                            setTextStyle(TextAnchor.CENTER, w/10 + getWorldview().width()*0.015, FontStyle.NORMAL);
                            draw(c, region.code);
                        }
                    }
//                if(l_region.getSize(layer) > getWorldview().width()*0.04){
//                    // Text in white
//                    //System.out.println(l_region.getSize(layer) - getWorldview().width()*0.03);
//                    setStroke(Color.WHITE, 1, null);
//                    setTextStyle(TextAnchor.CENTER, l_region.getSize(layer)/10 + getWorldview().width()*0.015, FontStyle.NORMAL);
//                    draw(l_region.getPosition(layer), l_region.code);
//                }
                }
                repaint();
            }


        }

    }

    private static double sigmoid(double x) {
        return 1 / (1 + Math.exp(-x));
    }

    @Override
    public Rectangle getBoundingRectangle() {
        Rectangle r = new Rectangle();
        for (int layer = 0; layer < c.getLayerCount(); layer++) {
            List<Region> regions = c.getRegions(layer);
            for (Region region : regions) {
                r.includeGeometry(region.getRectangle(layer));
            }
        }
        return r;
    }

    @Override
    protected void mousePress(Vector loc, int button, boolean ctrl, boolean shift, boolean alt) {
        if (button == 1) {
            // START OF DEBUGGING CODE - REMOVE!
            if(shift) {
                zoomToFit();
            } else {
                int active_layer = -1;
                for (int chosen_layer = 0; chosen_layer < c.getLayerCount(); chosen_layer++) {
                    if(sp.is_visible(chosen_layer)){
                        active_layer = chosen_layer;
                        break;
                    }
                }
                for (Region region : c.getRegions(active_layer)) {
                    if (region.getRectangle(active_layer).contains(loc, DoubleUtil.EPS)){
                        if (clicked_region_index == region.index) {
                            clicked_region_index = -1;
                        } else {
                            clicked_region_index = region.index;
                        }
                        break;
                    }
                }

                repaint();
                // END OF DEBUGGING CODE
            }
        }
        if(button == 3) {
//            zoomToFit();
        }
    }

    @Override
    protected void keyPress(int keycode, boolean ctrl, boolean shift, boolean alt) {

    }

    public void screenshot(String name) {
        System.out.println("\tto " + name);
        try (IPEWriter write = IPEWriter.fileWriter(new File(name))) {

            write.setWorldview(getBoundingRectangle());

            write.initialize();

            for (int i = 0; i < c.getLayerCount(); i++) {
                Arrays.fill(sp.visible_layers, false);
                sp.visible_layers[i] = true;

                write.newPage("regions", "labels", "obstacles", "obstacle_labels");

                render(write);
            }

        } catch (IOException ex) {
            Logger.getLogger(DrawPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
        Arrays.fill(sp.visible_layers, false);
        sp.visible_layers[0] = true;
    }
}
