package roll.learner.dpa.zielonka;

import java.util.ArrayList;
import java.util.List;


public class Node<T> {
    private List<Node<T>> children = new ArrayList<>();
    private Node<T> parent;
    private T data;
    
    public Node(T data) {
        this.data = data;
    }
    
    public Node(Node<T> parent, T data) {
        this.parent = parent;
        this.data = data;
    }
    
    public List<Node<T>> getChildren() {
        return children;
    }
    
    public void setParent(Node<T> parent) {
        parent.addChild(this);
        this.parent = parent;
    }
    
    public void addChild(T data) {
        Node<T> child = new Node<T>(data);
        child.setParent(this);
        this.children.add(child);
    }
    
    public void addChild(Node<T> child) {
        child.setParent(this);
        this.children.add(child);
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public boolean isRoot() {
        return (this.parent == null);
    }
    
    public boolean isLeaf() {
        return this.children.isEmpty();
    }
    
    public void removeParent() {
        this.parent = null;
    }
    
    public int getDepth(){
        if (parent == null)
        {
            return 0;
        } 
        else 
        {
            return parent.getDepth() + 1;
        }
    }
}