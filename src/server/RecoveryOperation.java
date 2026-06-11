package server;

/*
 * Le operazioni di recovery dal users_temp.log vengono salvate in oggetti di questa classe.
 */

public class RecoveryOperation {

    private String operation;
    private int id;
    private String first;
    private String second;

    public RecoveryOperation() {}

    public String getOperation() { return operation; }
    
    public int getId() { return id; }
    
    public String getFirst() { return first; }
    
    public String getSecond() { return second; }

    public void setOperation(String operation) { this.operation = operation; }
    
    public void setId(int id) { this.id = id; }
    
    public void setFirst(String first) { this.first = first; }
    
    public void setSecond(String second) { this.second = second; }
}