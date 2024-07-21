package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Table (name = "index")
@Entity
@Getter
@Setter
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
   private int id;

    @Column(name = "page_id")
   private int pageId;

    @Column(name = "lemma_id")
   private int lemmaId;

    @Column(name = "rank")
   private float rank;
}
