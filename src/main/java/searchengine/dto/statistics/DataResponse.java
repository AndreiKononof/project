package searchengine.dto.statistics;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DataResponse {
    private boolean result;
    private int count;
    private List<Data> data;
}
