package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.SiteDB;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    @Query (value = "SELECT path FROM Page Where site = :site")
    List<String> findAllPath (@Param("site") SiteDB siteDB);

    @Query(value = "Select id From Page Where path = :path and site = :site ")
    Optional<Integer> findIdPage(@Param("path") String path, @Param("site") SiteDB siteDB);
}
