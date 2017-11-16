package visualsearch.service;

public class IndexImageRequest {
    public String imageUrl;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IndexImageRequest that = (IndexImageRequest) o;

        return imageUrl != null ? imageUrl.equals(that.imageUrl) : that.imageUrl == null;
    }

    @Override
    public int hashCode() {
        return imageUrl != null ? imageUrl.hashCode() : 0;
    }

    public IndexImageRequest setUrl(String url) {
        this.imageUrl = url;
        return this;
    }
}
