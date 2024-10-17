package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Table(name = "lemma")
@Entity
@Getter
@Setter
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.REMOVE, fetch = FetchType.EAGER)
    private SiteDB site;

    @Column(name = "lemma", columnDefinition = "VARCHAR(255)")
    private String lemma;

    @Column(name = "frequency")
    private int frequency;

    @Override
    public String toString() {
        return id+" "+lemma;
    }
}
