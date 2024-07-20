package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Table (name = "page")
@Entity
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(name = "site_id")
    private int siteDBId;

    private String path;

    private int code;

    private String content;
}
