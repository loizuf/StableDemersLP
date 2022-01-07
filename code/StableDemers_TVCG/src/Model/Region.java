package Model;/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.Polygon;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometry.mix.GeometryGroup;

import java.awt.*;
import java.util.*;

/**
 *
 * @author wmeulema
 */
public class Region implements Comparable<Region> {

    /*
     * A class which saves information about a single region in a single layer
     */
    protected class LayerEntry {
        public Vector location;
        public double weight;

        /*
        public Set<Region> to_top;
        public Set<Region> to_bottom;
        public Set<Region> to_right;
        public Set<Region> to_left;
        */
        protected LayerEntry(Vector location, double weight) {
            this.location = location;
            this.weight = weight;
        }

        @Override
        public String toString() {
            return "{location=" + location + ", weight=" + weight + '}';
        }
    }


    // region information
    public int index;
    public String code;
    public String name;
    public Vector original_location;
    public Color color;
    private Rectangle input_bounding_box;
    private GeometryGroup<Polygon> input_polygon;
    private Map<Integer, LayerEntry> layer_entries;

    // THIS IS REDUNDANT INFORMATION SHOULD BE HANDLED IN CARTOGRAM MODEL
    private Set<Integer> layers_present;

    public boolean isObstacle = false;

    /**
     * Returns the square of this region in layer 'layer'
     *
     * @param layer
     * @return
     */
    public Rectangle getRectangle(int layer) {
        LayerEntry entry = layer_entries.get(layer);
        return Rectangle.byCenterAndSize(entry.location, entry.weight, entry.weight);
    }

    public Vector getPosition(int layer) {
        return layer_entries.get(layer).location;
    }

    /**
     * Returns the size of this region in layer 'layer'
     *
     * @param layer
     * @return
     */
    public double getSize(Integer layer) {
        return layer_entries.get(layer).weight;
    }

    /**
     * Returns this regions original location in the input map
     *
     * @return
     */
    public Vector getOriginalLocation() {
        return original_location;
    }

    /**
     * Returns this regions input bounding box
     * @return
     */
    public Rectangle getInputBoundingBox() { return input_bounding_box; }

    /**
     * Returns this regions input polygon
     * @return
     */
    public GeometryGroup<Polygon> getInputPolygon() { return input_polygon; }

    /**
     * Overloaded constructor. Name specification is not necessary.
     *
     * @param index
     * @param code
     * @param origin
     */
    public Region(int index, String code, Vector origin, double[] weights) {
        this(index, code, origin, weights, "No Name Given");
    }

    public Region(int index, String code, Vector origin, double[] weights, String name) {
        this(index, code, origin, new Color(80,80,80), weights, name);
    }
    /**
     * Standard constructor for a region.
     *
     * @param index
     * @param code
     * @param origin
     * @param name
     */
    public Region(int index, String code, Vector origin, Color color, double[] weights, String name) {
        this.index = index;
        this.code = code;
        this.original_location = origin;
        this.color = color;
        this.name = name;
        layers_present = new HashSet<>();
        layer_entries = new HashMap<>();
        for (int i = 0; i < weights.length; i++) {
            double weight = weights[i];
            if(weight != -1){
                layer_entries.put(i, new LayerEntry(new Vector(0, 0), Math.sqrt(weight)));
            }
        }
    }

    @Override
    public int compareTo(Region other) {
        return Integer.compare(this.index, other.index);
    }

    public void setAsObstacle() {
        isObstacle = true;
    }

    public boolean isPresent(int layer) {
        return layers_present.contains(layer);
    }

    public void setPresent(int layer) {
        layers_present.add(layer);
    }

    /**
     * Sets new position for this region in layer. Assumes value is of length two and x-position is at index 0 (y-position at index 1)
     * @param layer
     * @param new_position
     */
    public void setPosition(int layer, Vector new_position) {
        layer_entries.get(layer).location = new_position;
    }

    /**
     * Sets the input bounding box for this region
     * @param input_bounding_box
     */
    public void setInputBoundingBox(Rectangle input_bounding_box) {
        if(this.input_bounding_box != null && !this.input_bounding_box.center().isEqual(input_bounding_box.center())) System.out.println(this.input_bounding_box + " replaced by  " + input_bounding_box);
        this.input_bounding_box = input_bounding_box;
    }

    /**
     * Sets the input bounding box for this region
     * @param input_polygon
     */
    public void setInputPolygon(GeometryGroup<Polygon> input_polygon) {
        this.input_polygon = input_polygon;
    }


    /**
     * Sets new size for this region in layer. Assumes that region is an obstacle.
     * @param layer
     * @param value
     */
    public void setSize(int layer, Double value) {
        layer_entries.get(layer).weight = value;
    }


    @Override
    public String toString() {
        StringBuilder tostring = new StringBuilder("Region{" +
                "index=" + index +
                "\n\tcode='" + code + '\'' +
                "\n\tname='" + name + '\'' +
                "\n\toriginal_location=" + original_location +
                "\n\tinput_bounding_box=" + input_bounding_box +
                "\n\tisObstacle=" + isObstacle +
                "\n\tlayer_entries={");
        for (Integer integer : layers_present) {
            tostring.append("\n\t\t" + integer + ": " +layer_entries.get(integer).toString());
        }
        return tostring + "}\n}";
    }
}