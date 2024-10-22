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

    @ManyToOne(cascade = CascadeType.REMOVE, fetch = FetchType.EAGER)
    @JoinColumn(name = "page_id")
    private Page page;

    @ManyToOne(cascade = CascadeType.REMOVE, fetch = FetchType.EAGER)
    @JoinColumn(name = "lemma_id")
    private Lemma lemma;

    @Column(name = "rank_values")
    private float rank;

    @Override
    public String toString() {
        return page.getPath() +" "+ lemma.getLemma()+" "+ rank;
    }
}
