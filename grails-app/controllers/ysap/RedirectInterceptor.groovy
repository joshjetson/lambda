package ysap

class RedirectInterceptor {

    RedirectInterceptor() {
        // Match the controller/action pattern from UrlMappings
        match(controller: '*', action: '*')
    }

    boolean before() {
        // Get User-Agent and make it lowercase for checking
        String userAgent = request.getHeader("User-Agent")?.toLowerCase() ?: ""
        
        // Browser detection pattern (similar to your example)
        String browserPattern = ".*(firefox|chrome|safari|edge|opera|vivaldi|" +
                "xombrero|epiphany|gnome web|surf|webpositive|" +
                "msie|trident|ucbrowser|netscape|arachne|" +
                "sleipnir|mosaic|mozilla).*"

        // Log the access attempt
        def controllerName = params.controller ?: 'unknown'
        def actionName = params.action ?: 'unknown'
        
        switch (request.method) {
            case 'GET':
                // If the request is coming from a browser, redirect to root
                if (userAgent.matches(browserPattern)) {
                    log.warn("SECURITY: Blocked browser access attempt to /${controllerName}/${actionName} from IP: ${request.remoteAddr}, User-Agent: ${request.getHeader("User-Agent")}")
                    redirect(uri: '/')
                    return false
                }
                // Non-browser GET requests also blocked
                log.warn("SECURITY: Blocked non-browser GET request to /${controllerName}/${actionName} from IP: ${request.remoteAddr}")
                response.status = 404
                render(status: 404, text: "Not Found")
                return false
                
            case 'POST':
            case 'PUT':
            case 'DELETE':
            case 'PATCH':
                // Block all non-GET requests to any controller
                log.warn("SECURITY: Blocked ${request.method} request to /${controllerName}/${actionName} from IP: ${request.remoteAddr}")
                response.status = 403
                render(status: 403, text: "Forbidden")
                return false
                
            default:
                // Handle unsupported request methods
                log.warn("SECURITY: Blocked unsupported ${request.method} request to /${controllerName}/${actionName} from IP: ${request.remoteAddr}")
                response.status = 400
                render(status: 400, text: "Bad Request")
                return false
        }
        
        // Should never reach here, but just in case
        return false
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }
}