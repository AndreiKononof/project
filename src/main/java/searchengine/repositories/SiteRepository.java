package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteDB;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteDB, Integer> {

    @Query (value = "Select id from SiteDB Where url= :url")
    List<Integer> findByUrl (@Param("url") String url);

}
