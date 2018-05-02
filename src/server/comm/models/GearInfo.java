package server.comm.models;

public class GearInfo {

    public GearInfo(){}

    private int id;
    private String attach;
    private String name;
    private String detail;
    private String size;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAttach() {
        return attach;
    }

    public void setAttach(String attach) {
        this.attach = attach;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "GearInfo{" +
                "id=" + id +
                ", attach='" + attach + '\'' +
                ", name='" + name + '\'' +
                ", detail='" + detail + '\'' +
                ", size='" + size + '\'' +
                '}';
    }
}
