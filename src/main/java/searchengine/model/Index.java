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

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Page page;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Lemma lemma;

    @Column(name = "rank_values")
    private float rank;

    @Override
    public String toString() {
        return page.getPath() +" "+ lemma.getLemma()+" "+ rank;
    }
}
