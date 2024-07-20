package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Table(name = "site")
@Entity
@Getter
@Setter
public class SiteDB {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum")
    private StatusSait statusSait;
    @Column(name = "status_time")
    private LocalDateTime statusTime;
    @Column(name = "last_error")
    private String lastError;

    private String url;

    private String name;
}
