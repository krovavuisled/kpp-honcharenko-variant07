package ua.edu.nupp.kpp.waste.server.web;

import ua.edu.nupp.kpp.waste.server.model.Site;
import javax.servlet.annotation.WebServlet;

@WebServlet("/api/sites/*")
public final class SiteServlet extends CrudServlet<Site> {
    public SiteServlet() { super(Services.SITE, Site.class); }
}
