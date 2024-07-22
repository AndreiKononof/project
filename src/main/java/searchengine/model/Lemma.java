package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Table (name = "lemma")
@Entity
@Getter
@Setter
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "site_id")
    private int siteID;

    @Column(name = "lemma")
    private String lemma;

    @Column(name = "frequency")
    private int frequency;
}
