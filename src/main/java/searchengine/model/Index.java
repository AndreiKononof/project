package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Table(name = "index_site")
@Entity
@Getter
@Setter
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.DETACH, fetch = FetchType.EAGER)
    private Page page;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    private Lemma lemma;

    @Column(name = "rank_values")
    private float rank;

    @Override
    public String toString() {
        return page.getPath() +" "+ lemma.getLemma()+" "+ rank;
    }
}
