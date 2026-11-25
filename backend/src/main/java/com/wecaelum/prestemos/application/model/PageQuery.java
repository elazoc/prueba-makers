package com.wecaelum.prestemos.application.model;

public record PageQuery(int page, int size, String sortField, boolean ascending) {

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser al menos 1.");
        }
    }
}
