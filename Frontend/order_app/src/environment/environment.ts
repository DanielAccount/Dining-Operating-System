export const environment = {
    production: false,
    apiUrl: '/api',

    /**
     * Origin (or path prefix) that serves uploaded files.
     *
     * Empty string in development: the Angular dev server proxies /uploads
     * to the backend (see proxy.config.json), so relative URLs just work.
     *
     * In production, if the API is hosted on a different origin than the
     * Angular app (e.g. https://api.example.com), set this to that origin
     * so '/uploads/menu-images/x.jpg' resolves to
     * 'https://api.example.com/uploads/menu-items/x.jpg'.
     *
     * Leave empty when both are served from the same origin.
     */
    uploadBaseUrl: ''
}
