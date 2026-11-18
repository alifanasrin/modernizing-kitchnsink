package kitchen.sink.demo.dto.response;

import java.util.List;

public record PaginatedResponse<T>(List<T> data, int pageSize, int pageNo, int lastPage, long totalRecords, long totalPages) {

    public PaginatedResponse(List<T> data, int pageSize, int pageNo, long totalRecords, long totalPages) {
        this(data, pageSize, pageNo,
                pageSize > 0 ? (int) Math.ceil((double) totalRecords / pageSize) : 0, totalRecords, totalPages);
    }
}
