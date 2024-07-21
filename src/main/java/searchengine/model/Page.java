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

    @Column(name = "path")
    private String path;

    @Column(name = "code")
    private int code;

    @Column(name = "content")
    private String content;
}
