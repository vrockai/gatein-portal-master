/*
 * TODO: Dummy package containing future jquery dropdown menu plugin 
 */
(function($){
    $.fn.dropdownmenu = function(options) {
        
         var settings = $.extend({}, {
            // Selector for the menu-element.
            menuElement: "li",
            // Selector for the to-be-collapsed element inside this parent element.
            arrowElement: ".menuarrow",
            // Class which is set to the to-be-collapsed element when it's collapsed.
            categoryElement: ".menucategory",
            // Class which is set to the to-be-collapsed element when it's collapsed.
            collapsedClass: "open",
            // Class which is set to the to-be-collapsed element when it's expanded.
            expandedClass: "close"            
        }, options);
        
        
        $(this).children(settings.menuElement).each(function(){
            var menuelement = $(this);
		
            var initialstate = settings.collapsedClass;
		
            if ($(this).hasClass(settings.expandedClass))
            {
                intialstate = settings.expandedClass;
            }
            else if (!$(this).hasClass(settings.collapsedClass))
            {
                $(this).addClass(settings.collapsedClass);
            }
		
            if (initialstate == settings.expandedClass)
            {
                $(this).children(settings.arrowElement).toggle(function(){
                    closeMenu(menuelement);
                }, function(){
                    openMenu(menuelement);
                });
                $(this).children(settings.categoryElement).toggle(function(){
                    closeMenu(menuelement);
                }, function(){
                    openMenu(menuelement);
                });
            }
            else
            {
                $(this).children(settings.arrowElement).toggle(function(){
                    openMenu(menuelement);
                }, function(){
                    closeMenu(menuelement);
                });
                $(this).children(settings.categoryElement).toggle(function(){
                    closeMenu(menuelement);
                }, function(){
                    openMenu(menuelement);
                });
            }
        });
	
        function openMenu(menuelement)
        {
            $(menuelement).toggleClass(settings.expandedClass, true);
            $(menuelement).removeClass(settings.collapsedClass);
        }
	
        function closeMenu(menuelement)
        {
            $(menuelement).toggleClass(settings.collapsedClass, true);
            $(menuelement).removeClass(settings.expandedClass);
        }
        
    };
})(jQuery);